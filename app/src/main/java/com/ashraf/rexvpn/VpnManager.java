package com.ashraf.rexvpn;

import android.content.Context;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

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

    private static VpnManager instance;

    private final Context context;
    private final Backend backend;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private Config currentConfig;
    private final TunnelImpl tunnel = new TunnelImpl();

    private VpnManager(Context context) {
        this.context = context.getApplicationContext();
        this.backend = new GoBackend(this.context);
    }

    public static synchronized VpnManager getInstance(
            Context context) {
        if (instance == null) {
            instance = new VpnManager(context);
        }
        return instance;
    }

    public void connect(String configText, Callback callback) {
        if (callback == null) return;

        if (configText == null || configText.trim().isEmpty()) {
            postError(callback, "استورد ملف الكونفيج الأول.");
            return;
        }

        ProtocolCatalog.Detection detection =
                ProtocolCatalog.detect(configText);

        if (detection.engine != ProtocolCatalog.Engine.WIREGUARD) {
            String protocol = detection.protocol == null
                    ? "غير معروف" : detection.protocol;

            postError(callback,
                    "نوع الكونفيج: " + protocol
                    + "\n\n"
                    + "المحرك المناسب لهذا النوع لم يتم دمجه "
                    + "واختباره بالكامل حتى الآن."
                    + "\n\n"
                    + "لم يتم بدء الاتصال. استخدم كونفيج "
                    + "WireGuard حاليًا، أو انتظر دعم المحرك.");
            return;
        }

        if (VpnService.prepare(context) != null) {
            mainHandler.post(callback::onPermissionRequired);
            return;
        }

        executor.execute(() -> {
            try {
                Config parsed = Config.parse(
                        new ByteArrayInputStream(
                                configText.getBytes(
                                        StandardCharsets.UTF_8)));

                Tunnel.State state = backend.setState(
                        tunnel,
                        Tunnel.State.UP,
                        parsed);

                if (state == Tunnel.State.UP) {
                    currentConfig = parsed;
                    mainHandler.post(() ->
                            callback.onSuccess(
                                    "تم تشغيل نفق WireGuard."));
                } else {
                    mainHandler.post(() ->
                            callback.onError(
                                    "المحرك لم يؤكد تشغيل النفق."));
                }

            } catch (Exception e) {
                postError(callback,
                        "فشل الاتصال: " + safeMessage(e));
            }
        });
    }

    public void disconnect(Callback callback) {
        executor.execute(() -> {
            try {
                backend.setState(
                        tunnel,
                        Tunnel.State.DOWN,
                        null);

                currentConfig = null;

                if (callback != null) {
                    mainHandler.post(() ->
                            callback.onSuccess(
                                    "تم فصل اتصال VPN."));
                }

            } catch (Exception e) {
                postError(callback,
                        "تعذر فصل الاتصال: " + safeMessage(e));
            }
        });
    }

    public boolean isConnected() {
        try {
            return backend.getState(tunnel) == Tunnel.State.UP;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEngineVersion() {
        try {
            return backend.getVersion();
        } catch (Exception e) {
            return "غير متاح";
        }
    }

    public String getCurrentEngine() {
        return "WireGuard";
    }

    private void postError(Callback callback, String message) {
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
            // الحالة الفعلية تُقرأ من محرك WireGuard.
        }
    }
}
