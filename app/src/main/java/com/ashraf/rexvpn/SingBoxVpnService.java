
package com.ashraf.rexvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.nekohasekai.libbox.CommandServer;
import io.nekohasekai.libbox.CommandServerHandler;
import io.nekohasekai.libbox.OverrideOptions;
import io.nekohasekai.libbox.PlatformInterface;

public final class SingBoxVpnService extends VpnService {

    public static final String ACTION_START =
            "com.ashraf.rexvpn.action.SINGBOX_START";

    public static final String ACTION_STOP =
            "com.ashraf.rexvpn.action.SINGBOX_STOP";

    public static final String EXTRA_CONFIG =
            "com.ashraf.rexvpn.extra.SINGBOX_CONFIG";

    public static final String EXTRA_RESULT =
            "com.ashraf.rexvpn.extra.SINGBOX_RESULT";

    private static final String TAG = "REX-SINGBOX";
    private static final String CHANNEL_ID = "rex_singbox_vpn";
    private static final int NOTIFICATION_ID = 7301;

    private CommandServer commandServer;
    private ParcelFileDescriptor tunDescriptor;
    private String currentConfig;
    private boolean stopping;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        if (intent == null || intent.getAction() == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopEngine();
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String config = intent.getStringExtra(EXTRA_CONFIG);

        if (config == null || config.trim().isEmpty()) {
            sendResult("خطأ: الكونفيج فارغ.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        try {
            currentConfig = SingBoxConfigBuilder
                    .fromJson(config)
                    .toString();

            stopping = false;

            startForeground(
                    NOTIFICATION_ID,
                    buildNotification("جاري تشغيل Sing-box...")
            );

            startEngine(currentConfig);

            sendResult("تم إرسال الكونفيج إلى محرك Sing-box.");

        } catch (Exception e) {
            Log.e(TAG, "Unable to start Sing-box", e);
            sendResult("فشل تشغيل Sing-box: " + safeMessage(e));
            stopEngine();
        }

        return START_NOT_STICKY;
    }

    private synchronized void startEngine(String config)
            throws Exception {

        if (VpnService.prepare(this) != null) {
            throw new IllegalStateException(
                    "إذن VPN غير ممنوح. وافق على طلب الاتصال أولًا."
            );
        }

        closeCommandServer();

        PlatformInterface platform = createPlatformInterface();
        CommandServerHandler handler = createCommandHandler();

        commandServer = new CommandServer(handler, platform);
        commandServer.start();

        try {
            commandServer.startOrReloadService(
                    config,
                    new OverrideOptions()
            );
        } catch (Exception e) {
            closeCommandServer();
            throw e;
        }
    }

    private PlatformInterface createPlatformInterface() {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();

            if ("toString".equals(name)) {
                return "REX VPN Platform";
            }

            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }

            if ("equals".equals(name)) {
                return proxy == args[0];
            }

            if ("openTun".equals(name)) {
                if (args == null || args.length == 0) {
                    throw new IllegalArgumentException(
                            "Sing-box لم يرسل إعدادات TUN."
                    );
                }

                closeTun();

                tunDescriptor = SingBoxPlatform.establishTun(
                        SingBoxVpnService.this,
                        args[0],
                        "REX VPN"
                );

                return tunDescriptor.getFd();
            }

            if ("autoDetectInterfaceControl".equals(name)) {
                if (args != null && args.length > 0
                        && args[0] instanceof Number) {
                    protect(((Number) args[0]).intValue());
                }
                return null;
            }

            if ("usePlatformAutoDetectInterfaceControl".equals(name)) {
                return true;
            }

            if ("underNetworkExtension".equals(name)
                    || "includeAllNetworks".equals(name)
                    || "useProcFS".equals(name)
                    || "usePlatformShell".equals(name)
                    || "usePlatformBridge".equals(name)
                    || "usePlatformAutoRedirect".equals(name)) {
                return false;
            }

            if ("startDefaultInterfaceMonitor".equals(name)
                    || "closeDefaultInterfaceMonitor".equals(name)
                    || "clearDNSCache".equals(name)
                    || "registerMyInterface".equals(name)
                    || "startNeighborMonitor".equals(name)
                    || "closeNeighborMonitor".equals(name)) {
                return null;
            }

            if ("tailscaleHostname".equals(name)) {
                return "REX-VPN";
            }

            if ("lookupSFTPServer".equals(name)
                    || "readSystemSSHHostKey".equals(name)) {
                return "";
            }

            return defaultValue(method.getReturnType());
        };

        return (PlatformInterface) Proxy.newProxyInstance(
                PlatformInterface.class.getClassLoader(),
                new Class<?>[]{PlatformInterface.class},
                handler
        );
    }

    private CommandServerHandler createCommandHandler() {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();

            if ("toString".equals(name)) {
                return "REX VPN Command Handler";
            }

            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }

            if ("equals".equals(name)) {
                return proxy == args[0];
            }

            if ("serviceStop".equals(name)) {
                stopEngine();
                return null;
            }

            if ("serviceReload".equals(name)) {
                String config = currentConfig;

                if (config != null && !stopping) {
                    startEngine(config);
                }

                return null;
            }

            return defaultValue(method.getReturnType());
        };

        return (CommandServerHandler) Proxy.newProxyInstance(
                CommandServerHandler.class.getClassLoader(),
                new Class<?>[]{CommandServerHandler.class},
                handler
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }

        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';

        return null;
    }

    private synchronized void closeCommandServer() {
        if (commandServer == null) {
            return;
        }

        try {
            commandServer.closeService();
        } catch (Exception e) {
            Log.w(TAG, "Could not close Sing-box service", e);
        }

        try {
            commandServer.close();
        } catch (Exception e) {
            Log.w(TAG, "Could not close command server", e);
        }

        commandServer = null;
    }

    private synchronized void closeTun() {
        if (tunDescriptor == null) {
            return;
        }

        try {
            tunDescriptor.close();
        } catch (Exception e) {
            Log.w(TAG, "Could not close TUN descriptor", e);
        }

        tunDescriptor = null;
    }

    private synchronized void stopEngine() {
        if (stopping) {
            return;
        }

        stopping = true;
        currentConfig = null;

        closeCommandServer();
        closeTun();

        try {
            stopForeground(true);
        } catch (Exception e) {
            Log.w(TAG, "Could not stop foreground notification", e);
        }

        sendResult("تم طلب فصل اتصال Sing-box.");
        stopSelf();
    }

    private void sendResult(String message) {
        Intent result = new Intent(EXTRA_RESULT);
        result.setPackage(getPackageName());
        result.putExtra(EXTRA_RESULT, message);
        sendBroadcast(result);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "REX VPN",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Notification.Builder builder =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Notification.Builder(this, CHANNEL_ID)
                        : new Notification.Builder(this);

        return builder
                .setContentTitle("REX VPN")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .build();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }

    @Override
    public void onRevoke() {
        stopEngine();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopEngine();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
