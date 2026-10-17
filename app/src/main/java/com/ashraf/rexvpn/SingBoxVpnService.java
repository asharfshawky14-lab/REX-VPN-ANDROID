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

import org.json.JSONObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;

import io.nekohasekai.libbox.CommandServer;
import io.nekohasekai.libbox.CommandServerHandler;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.OverrideOptions;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.SystemProxyStatus;

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
    private boolean engineStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
            JSONObject parsed = SingBoxConfigBuilder.fromJson(config);
            currentConfig = parsed.toString();

            startForeground(
                    NOTIFICATION_ID,
                    buildNotification("جاري تشغيل محرك Sing-box")
            );

            startEngine(currentConfig);
            engineStarted = true;
            sendResult("تم إرسال الكونفيج إلى محرك Sing-box.");

        } catch (Exception e) {
            Log.e(TAG, "Sing-box startup failed", e);
            sendResult("فشل تشغيل Sing-box: " + safeMessage(e));
            stopEngine();
        }

        return START_NOT_STICKY;
    }

    private synchronized void startEngine(String config) throws Exception {
        if (VpnService.prepare(this) != null) {
            throw new IllegalStateException(
                    "إذن VPN غير ممنوح. وافق على طلب الاتصال أولًا."
            );
        }

        stopCommandServer();

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
            stopCommandServer();
            throw e;
        }
    }

    private PlatformInterface createPlatformInterface() {
        InvocationHandler invocation = (proxy, method, args) -> {
            String name = method.getName();

            if ("toString".equals(name)) return "REX Sing-box Platform";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];

            if ("autoDetectInterfaceControl".equals(name)) {
                if (args != null && args.length > 0) {
                    protect((Integer) args[0]);
                }
                return null;
            }

            if ("usePlatformAutoDetectInterfaceControl".equals(name)) {
                return true;
            }

            if ("openTun".equals(name)) {
                return openTunFromOptions(args[0]);
            }

            if ("useProcFS".equals(name)
                    || "underNetworkExtension".equals(name)
                    || "includeAllNetworks".equals(name)
                    || "usePlatformShell".equals(name)
                    || "usePlatformBridge".equals(name)
                    || "usePlatformAutoRedirect".equals(name)) {
                return false;
            }

            if ("startDefaultInterfaceMonitor".equals(name)
                    || "closeDefaultInterfaceMonitor".equals(name)
                    || "clearDNSCache".equals(name)
                    || "registerMyInterface".equals(name)
                    || "closeNeighborMonitor".equals(name)) {
                return null;
            }

            if ("getInterfaces".equals(name)) {
                return emptyIterator(
                        "io.nekohasekai.libbox.NetworkInterfaceIterator"
                );
            }

            if ("findConnectionOwner".equals(name)) {
                return Class.forName(
                        "io.nekohasekai.libbox.ConnectionOwner"
                ).getDeclaredConstructor().newInstance();
            }

            if ("readWIFIState".equals(name)
                    || "localDNSTransport".equals(name)) {
                return null;
            }

            if ("tailscaleHostname".equals(name)) {
                return "REX-VPN";
            }

            if ("lookupSFTPServer".equals(name)
                    || "readSystemSSHHostKey".equals(name)) {
                return "";
            }

            if ("startNeighborMonitor".equals(name)) return null;

            if ("lookupUser".equals(name)) {
                throw new UnsupportedOperationException(
                        "Platform user lookup is not supported."
                );
            }

            if ("openShellSession".equals(name)
                    || "createBridge".equals(name)
                    || "createAutoRedirect".equals(name)
                    || "checkPlatformShell".equals(name)) {
                throw new UnsupportedOperationException(
                        "This optional platform feature is unavailable."
                );
            }

            return defaultValue(method.getReturnType());
        };

        return (PlatformInterface) Proxy.newProxyInstance(
                PlatformInterface.class.getClassLoader(),
                new Class<?>[]{PlatformInterface.class},
                invocation
        );
    }

    private Object createCommandHandler() {
        InvocationHandler invocation = (proxy, method, args) -> {
            String name = method.getName();

            if ("toString".equals(name)) return "REX Sing-box Handler";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];

            if ("serviceStop".equals(name)) {
                stopEngine();
                return null;
            }

            if ("serviceReload".equals(name)) {
                String config = currentConfig;
                if (config != null) {
                    startEngine(config);
                }
                return null;
            }

            if ("getSystemProxyStatus".equals(name)) {
                SystemProxyStatus status = new SystemProxyStatus();
                status.setAvailable(false);
                status.setEnabled(false);
                return status;
            }

            if ("setSystemProxyEnabled".equals(name)
                    || "triggerNativeCrash".equals(name)
                    || "writeDebugMessage".equals(name)) {
                return null;
            }

            if ("connectSSHAgent".equals(name)) {
                return -1;
            }

            return defaultValue(method.getReturnType());
        };

        return Proxy.newProxyInstance(
                CommandServerHandler.class.getClassLoader(),
                new Class<?>[]{CommandServerHandler.class},
                invocation
        );
    }

    private int openTunFromOptions(Object options) throws Exception {
        if (VpnService.prepare(this) != null) {
            throw new IllegalStateException("إذن VPN غير ممنوح.");
        }

        int mtu = ((Number) invoke(options, "getMtu")).intValue();
        android.net.VpnService.Builder builder = new Builder()
                .setSession("REX VPN")
                .setMtu(Math.max(1280, Math.min(mtu, 9000)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        addAddresses(builder, invoke(options, "getInet4Address"));
        addAddresses(builder, invoke(options, "getInet6Address"));

        Object autoRoute = invoke(options, "getAutoRoute");
        if (Boolean.TRUE.equals(autoRoute)) {
            Object dnsMode = invoke(options, "getDnsMode");
            Object dnsServer = invoke(options, "getDnsServerAddress");

            boolean dnsDisabled = false;
            if (dnsMode != null) {
                try {
                    int mode = ((Number) invoke(dnsMode, "value")).intValue();
                    dnsDisabled = mode == Libbox.DNSModeDisabled;
                } catch (Exception ignored) {
                    // Continue using DNS settings from the TUN options.
                }
            }

            if (!dnsDisabled) {
                addDnsServers(builder, dnsServer);
            }

            addRoutes(builder, options, "getInet4RouteRange", false);
            addRoutes(builder, options, "getInet6RouteRange", false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addRoutes(builder, options, "getInet4RouteAddress", true);
                addRoutes(builder, options, "getInet6RouteAddress", true);
                addRoutes(builder, options, "getInet4RouteExcludeAddress", true);
                addRoutes(builder, options, "getInet6RouteExcludeAddress", true);
            }
        }

        ParcelFileDescriptor descriptor = builder.establish();
        if (descriptor == null) {
            throw new IllegalStateException(
                    "Android رفض إنشاء نفق TUN."
            );
        }

        if (tunDescriptor != null) {
            try {
                tunDescriptor.close();
            } catch (Exception ignored) {
            }
        }

        tunDescriptor = descriptor;
        return descriptor.getFd();
    }

    private void addAddresses(
            VpnService.Builder builder,
            Object iterator
    ) throws Exception {
        while (hasNext(iterator)) {
            Object address = next(iterator);
            String ip = String.valueOf(invoke(address, "address"));
            int prefix = ((Number) invoke(address, "prefix")).intValue();
            builder.addAddress(ip, prefix);
        }
    }

    private void addDnsServers(
            VpnService.Builder builder,
            Object iterator
    ) throws Exception {
        while (hasNext(iterator)) {
            String dns = String.valueOf(next(iterator));
            if (!dns.isEmpty()) {
                builder.addDnsServer(dns);
            }
        }
    }

    private void addRoutes(
            VpnService.Builder builder,
            Object options,
            String getter,
            boolean cidrStrings
    ) throws Exception {
        Object iterator;
        try {
            iterator = invoke(options, getter);
        } catch (Exception ignored) {
            return;
        }

        if (iterator == null) return;

        while (hasNext(iterator)) {
            Object route = next(iterator);
            if (route == null) continue;

            if (cidrStrings) {
                String cidr = String.valueOf(route);
                String[] parts = cidr.split("/");
                if (parts.length == 2) {
                    int prefix = Integer.parseInt(parts[1]);
                    if (getter.contains("Exclude")) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            builder.excludeRoute(
                                    android.net.IpPrefix.class
                                            .getConstructor(String.class, int.class)
                                            .newInstance(parts[0], prefix)
                            );
                        }
                    } else {
                        builder.addRoute(parts[0], prefix);
                    }
                }
            } else {
                String ip = String.valueOf(invoke(route, "address"));
                int prefix = ((Number) invoke(route, "prefix")).intValue();
                builder.addRoute(ip, prefix);
            }
        }
    }

    private Object emptyIterator(String className) throws Exception {
        Class<?> type = Class.forName(className);
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("hasNext".equals(method.getName())) return false;
                    if ("next".equals(method.getName())) return null;
                    if ("len".equals(method.getName())) return 0;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private boolean hasNext(Object iterator) throws Exception {
        return iterator != null && Boolean.TRUE.equals(invoke(iterator, "hasNext"));
    }

    private Object next(Object iterator) throws Exception {
        return invoke(iterator, "next");
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
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

    private synchronized void stopCommandServer() {
        if (commandServer != null) {
            try {
                commandServer.closeService();
            } catch (Exception e) {
                Log.w(TAG, "closeService", e);
            }

            try {
                commandServer.close();
            } catch (Exception e) {
                Log.w(TAG, "close", e);
            }

            commandServer = null;
        }
    }

    private synchronized void stopEngine() {
        engineStarted = false;
        stopCommandServer();

        if (tunDescriptor != null) {
            try {
                tunDescriptor.close();
            } catch (Exception e) {
                Log.w(TAG, "TUN close", e);
            }
            tunDescriptor = null;
        }

        stopForeground(true);
        stopSelf();
        sendResult("تم طلب فصل اتصال Sing-box.");
    }

    private void sendResult(String message) {
        Intent result = new Intent(EXTRA_RESULT);
        result.setPackage(getPackageName());
        result.putExtra(EXTRA_RESULT, message);
        sendBroadcast(result);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "REX VPN",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
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
