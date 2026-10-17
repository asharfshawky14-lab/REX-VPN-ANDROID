
package com.ashraf.rexvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;

/**
 * نقطة البداية لخدمة Sing-box في REX VPN.
 *
 * ملاحظة: الخدمة تجهّز هيكل التشغيل فقط حاليًا.
 * لم يتم ربط CommandServer أو واجهة TUN الخاصة بمكتبة
 * libbox بعد، ولذلك لا تنشئ نفق Sing-box فعليًا حتى الآن.
 */
public final class SingBoxVpnService extends VpnService {

    public static final String ACTION_START =
            "com.ashraf.rexvpn.action.SINGBOX_START";

    public static final String ACTION_STOP =
            "com.ashraf.rexvpn.action.SINGBOX_STOP";

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

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
            );

            // لا يبدأ اتصالًا فعليًا حتى دمج libbox
            // والتحقق من إعداداته وواجهة TUN.
            stopForeground(true);
            stopSelf(startId);
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "REX VPN",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "إشعارات خدمة VPN"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("REX VPN")
                .setContentText("خدمة Sing-box قيد التجهيز")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
