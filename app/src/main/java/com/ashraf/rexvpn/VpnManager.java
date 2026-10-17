
package com.ashraf.rexvpn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VpnManager {

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
        void onPermissionRequired();
    }

    private static final String PREFS_NAME = "rex_vpn_runtime";
    private static final String KEY_SINGBOX_REQUESTED =
            "singbox_requested";

    private static VpnManager instance;

    private final Context context;
    private final Backend wireGuardBackend;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final SharedPreferences runtimePrefs;

    private Config currentWireGuardConfig;
    private final TunnelImpl wireGuardTunnel = new TunnelImpl();

    private VpnManager(Context context) {
        this.context = context.getApplicationContext();

        this.wireGuardBackend = new GoBackend(this.context);

        this.runtimePrefs = this.context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    public static synchronized VpnManager getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = new VpnManager(context);
        }

        return instance;
    }

    public void connect(String configText, Callback callback) {
        if (callback == null) {
            return;
        }

        if (configText == null || configText.trim().isEmpty()) {
            postError(callback, "استورد ملف الكونفيج الأول.");
            return;
        }

        if (VpnService.prepare(context) != null) {
            mainHandler.post(callback::onPermissionRequired);
            return;
        }

        ProtocolCatalog.Detection detection =
                ProtocolCatalog.detect(configText);

        if (detection.engine == ProtocolCatalog.Engine.WIREGUARD) {
            connectWireGuard(configText, callback);
            return;
        }

        if (detection.engine == ProtocolCatalog.Engine.SING_BOX
                || looksLikeSingBoxJson(configText)) {
            connectSingBox(configText, callback);
            return;
        }

        String protocol = detection.protocol == null
                ? "غير معروف"
                : detection.protocol;

        postError(
                callback,
                "نوع الكونفيج غير مدعوم للتشغيل حاليًا: "
                        + protocol
                        + "\nتأكد من صيغة الملف أو استخدم كونفيج مدعوم."
        );
    }

    private void connectWireGuard(
            String configText,
            Callback callback
    ) {
        executor.execute(() -> {
            try {
                stopSingBoxIfRequested();

                Config parsed = Config.parse(
                        new ByteArrayInputStream(
                                configText.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                );

                Tunnel.State state = wireGuardBackend.setState(
                        wireGuardTunnel,
                        Tunnel.State.UP,
                        parsed
                );

                if (state == Tunnel.State.UP) {
                    currentWireGuardConfig = parsed;

                    mainHandler.post(() ->
                            callback.onSuccess(
                                    "تم تشغيل نفق WireGuard."
                            )
                    );
                } else {
                    postError(
                            callback,
                            "محرك WireGuard لم يؤكد تشغيل النفق."
                    );
                }

            } catch (Exception e) {
                postError(
                        callback,
                        "فشل اتصال WireGuard: " + safeMessage(e)
                );
            }
        });
    }

    private void connectSingBox(
            String configText,
            Callback callback
    ) {
        executor.execute(() -> {
            try {
                stopWireGuardIfRunning();

                String jsonConfig = prepareSingBoxConfig(configText);

                Intent intent = new Intent(
                        context,
                        SingBoxVpnService.class
                );

                intent.setAction(SingBoxVpnService.ACTION_START);
                intent.putExtra(
                        SingBoxVpnService.EXTRA_CONFIG,
                        jsonConfig
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }

                runtimePrefs.edit()
                        .putBoolean(KEY_SINGBOX_REQUESTED, true)
                        .apply();

                mainHandler.post(() ->
                        callback.onSuccess(
                                "تم إرسال طلب تشغيل Sing-box. "
                                        + "جارٍ التحقق من بدء الاتصال."
                        )
                );

            } catch (Exception e) {
                runtimePrefs.edit()
                        .putBoolean(KEY_SINGBOX_REQUESTED, false)
                        .apply();

                postError(
                        callback,
                        "فشل بدء Sing-box: " + safeMessage(e)
                );
            }
        });
    }

    private String prepareSingBoxConfig(String input)
            throws Exception {

        String value = input.trim();

        if (value.startsWith("{")) {
            return SingBoxConfigBuilder
                    .fromJson(value)
                    .toString();
        }

        String protocol =
                SingBoxConfigBuilder.detectProtocol(value);

        if ("vless".equals(protocol)) {
            return SingBoxConfigBuilder
                    .fromVlessLink(value)
                    .toString();
        }

        if ("vmess".equals(protocol)
                || "trojan".equals(protocol)
                || "shadowsocks".equals(protocol)
                || "hysteria2".equals(protocol)
                || "tuic".equals(protocol)
                || "anytls".equals(protocol)
                || "socks".equals(protocol)
                || "http".equals(protocol)) {
            throw new IllegalArgumentException(
                    "تم التعرف على نوع الكونفيج، لكن تحويل هذا الرابط "
                            + "إلى إعدادات Sing-box لم يُنفذ بعد: "
                            + protocol
            );
        }

        throw new IllegalArgumentException(
                "صيغة Sing-box غير معروفة. استخدم JSON صالحًا "
                        + "أو رابط VLESS مدعومًا."
        );
    }

    private boolean looksLikeSingBoxJson(String value) {
        String text = value == null ? "" : value.trim();

        return text.startsWith("{")
                && text.contains("\"inbounds\"")
                && text.contains("\"outbounds\"");
    }

    public void disconnect(Callback callback) {
        executor.execute(() -> {
            Exception failure = null;

            try {
                wireGuardBackend.setState(
                        wireGuardTunnel,
                        Tunnel.State.DOWN,
                        null
                );

                currentWireGuardConfig = null;

            } catch (Exception e) {
                failure = e;
            }

            try {
                Intent intent = new Intent(
                        context,
                        SingBoxVpnService.class
                );

                intent.setAction(SingBoxVpnService.ACTION_STOP);
                context.startService(intent);

                runtimePrefs.edit()
                        .putBoolean(KEY_SINGBOX_REQUESTED, false)
                        .apply();

            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
            }

            if (callback == null) {
                return;
            }

            if (failure == null) {
                mainHandler.post(() ->
                        callback.onSuccess("تم طلب فصل اتصال VPN.")
                );
            } else {
                String error = safeMessage(failure);

                mainHandler.post(() ->
                        callback.onError(
                                "حدث خطأ أثناء الفصل: " + error
                        )
                );
            }
        });
    }

    private void stopWireGuardIfRunning() throws Exception {
        if (wireGuardBackend.getState(wireGuardTunnel)
                == Tunnel.State.UP) {
            wireGuardBackend.setState(
                    wireGuardTunnel,
                    Tunnel.State.DOWN,
                    null
            );
        }

        currentWireGuardConfig = null;
    }

    private void stopSingBoxIfRequested() {
        if (!runtimePrefs.getBoolean(
                KEY_SINGBOX_REQUESTED,
                false
        )) {
            return;
        }

        Intent intent = new Intent(
                context,
                SingBoxVpnService.class
        );

        intent.setAction(SingBoxVpnService.ACTION_STOP);

        try {
            context.startService(intent);
        } catch (Exception e) {
            android.util.Log.w(
                    "REX-VPN",
                    "Could not request Sing-box stop",
                    e
            );
        }

        runtimePrefs.edit()
                .putBoolean(KEY_SINGBOX_REQUESTED, false)
                .apply();
    }

    public boolean isConnected() {
        try {
            if (runtimePrefs.getBoolean(
                    KEY_SINGBOX_REQUESTED,
                    false
            )) {
                // هذه علامة على طلب التشغيل فقط، وليست تأكيدًا
                // أن نفق Sing-box يعمل فعليًا.
                return true;
            }

            return wireGuardBackend.getState(wireGuardTunnel)
                    == Tunnel.State.UP;

        } catch (Exception e) {
            return false;
        }
    }

    public String getEngineVersion() {
        try {
            return wireGuardBackend.getVersion();
        } catch (Exception e) {
            return "غير متاح";
        }
    }

    public String getCurrentEngine() {
        if (runtimePrefs.getBoolean(
                KEY_SINGBOX_REQUESTED,
                false
        )) {
            return "Sing-box";
        }

        return "WireGuard";
    }

    private void postError(
            Callback callback,
            String message
    ) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }

    private static final class TunnelImpl implements Tunnel {

        @Override
        public String getName() {
            return "REX-VPN";
        }

        @Override
        public void onStateChange(Tunnel.State state) {
            // تُقرأ حالة WireGuard من المحرك نفسه.
        }
    }
}
