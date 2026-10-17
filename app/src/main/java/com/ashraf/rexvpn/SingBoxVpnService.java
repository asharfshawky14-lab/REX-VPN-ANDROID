package com.ashraf.rexvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

public final class SingBoxVpnService extends VpnService {

    public static final String ACTION_START =
            "com.ashraf.rexvpn.action.SINGBOX_START";

    public static final String ACTION_STOP =
            "com.ashraf.rexvpn.action.SINGBOX_STOP";

    public static final String EXTRA_CONFIG =
            "com.ashraf.rexvpn.extra.SINGBOX_CONFIG";

    public static final String EXTRA_RESULT =
            "com.ashraf.rexvpn.extra.SINGBOX_RESULT";

    private static final String CHANNEL_ID = "rex_singbox_vpn";
    private static final int NOTIFICATION_ID = 7301;

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
            stopForeground(true);
            stopSelf();
            sendResult("تم إيقاف الخدمة.");
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification("جاري تجهيز اتصال Sing-box")
        );

        String configText = intent.getStringExtra(EXTRA_CONFIG);

        try {
            if (configText == null || configText.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "إعدادات الاتصال فارغة."
                );
            }

            JSONObject config =
                    SingBoxConfigBuilder.fromJson(configText);

            // الفحص هنا فقط؛ تشغيل المحرك الفعلي يحتاج
            // CommandServer وPlatformInterface وفتح نفق TUN.
            Log.i("REX-SINGBOX",
                    "Configuration validated: "
                            + config.optJSONArray("outbounds"));

            sendResult(
                    "الإعدادات سليمة، لكن المحرك الفعلي لم يبدأ بعد."
            );

        } catch (Exception e) {
            String message = e.getMessage();

            if (message == null || message.trim().isEmpty()) {
                message = "تعذر تجهيز إعدادات الاتصال.";
            }

            Log.e("REX-SINGBOX", "Startup failed", e);
            sendResult("خطأ: " + message);

        } finally {
            stopForeground(true);
            stopSelf(startId);
        }

        return START_NOT_STICKY;
    }

    private void sendResult(String message) {
        Intent response = new Intent(EXTRA_RESULT);
        response.setPackage(getPackageName());
        response.putExtra(EXTRA_RESULT, message);
        sendBroadcast(response);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "REX VPN",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("إشعارات خدمة REX VPN");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String message) {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("REX VPN")
                .setContentText(message)
                .setSmallIcon(
                        android.R.drawable.stat_sys_download_done
                )
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
