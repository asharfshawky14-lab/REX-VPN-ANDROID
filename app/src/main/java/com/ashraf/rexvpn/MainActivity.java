package com.ashraf.rexvpn;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private static final int REQUEST_VPN = 501;
    private static final int REQUEST_CONFIG = 502;

    private final int BG = Color.rgb(8, 8, 18);
    private final int CARD = Color.rgb(20, 19, 37);
    private final int PURPLE = Color.rgb(145, 75, 255);
    private final int CYAN = Color.rgb(0, 229, 255);
    private final int MUTED = Color.rgb(170, 170, 195);
    private final int WHITE = Color.WHITE;

    private VpnManager vpnManager;
    private SharedPreferences prefs;

    private TextView status;
    private TextView configName;
    private TextView connectButton;
    private TextView detail;
    private boolean waitingForPermission = false;

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(
            int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) {
            d.setStroke(dp(1), border);
        }
        return d;
    }

    private TextView text(
            String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void message(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void section(LinearLayout root, String title) {
        TextView t = text(title, 18, WHITE, true);
        LinearLayout.LayoutParams p = lp(-1, dp(48));
        p.topMargin = dp(8);
        root.addView(t, p);
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(shape(CARD, Color.rgb(58, 45, 90), 18));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(12);
        root.addView(c, p);
        return c;
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE, 0, 13));
        return b;
    }

    private void addLine(LinearLayout parent, String value) {
        TextView t = text(value, 14, MUTED, false);
        LinearLayout.LayoutParams p = lp(-1, dp(34));
        parent.addView(t, p);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vpnManager = VpnManager.getInstance(this);
        prefs = getSharedPreferences("rex_settings", MODE_PRIVATE);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));

        LinearLayout brand = column();
        TextView logo = text("REX VPN", 27, CYAN, true);
        TextView tagline = text(
                "POWER BEHIND THE SHADOW", 9, MUTED, true);
        brand.addView(logo);
        brand.addView(tagline);

        header.addView(brand,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView menu = text("☰", 27, WHITE, true);
        menu.setGravity(Gravity.CENTER);
        header.addView(menu, lp(dp(48), dp(48)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout body = column();
        body.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(shape(CARD, PURPLE, 22));
        hero.setClipToOutline(true);

        ImageView wolf = new ImageView(this);
        int wolfId = getResources().getIdentifier(
                "wolf_background", "drawable", getPackageName());

        if (wolfId != 0) {
            wolf.setImageResource(wolfId);
            wolf.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.addView(wolf, new FrameLayout.LayoutParams(
                    -1, -1));
        } else {
            TextView fallback = text("🐺", 82, PURPLE, true);
            fallback.setGravity(Gravity.CENTER);
            hero.addView(fallback,
                    new FrameLayout.LayoutParams(-1, -1));
        }

        TextView overlay = text(
                "ENTER THE SHADOW", 16, WHITE, true);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0x77080812);

        FrameLayout.LayoutParams overlayParams =
                new FrameLayout.LayoutParams(-1, dp(48),
                        Gravity.BOTTOM);
        hero.addView(overlay, overlayParams);

        LinearLayout.LayoutParams heroParams = lp(-1, dp(205));
        heroParams.bottomMargin = dp(16);
        body.addView(hero, heroParams);

        LinearLayout connectionCard = card(body);

        status = text("● غير متصل", 18, MUTED, true);
        status.setGravity(Gravity.CENTER);
        connectionCard.addView(status, lp(-1, dp(38)));

        detail = text(
                "الاتصال غير نشط. حركة الإنترنت ليست محمية بواسطة VPN.",
                12, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        connectionCard.addView(detail, lp(-1, dp(48)));

        FrameLayout power = new FrameLayout(this);
        power.setBackground(shape(
                Color.rgb(39, 22, 68), PURPLE, 100));

        LinearLayout powerInner = column();
        powerInner.setGravity(Gravity.CENTER);

        connectButton = text("⏻", 54, CYAN, true);
        connectButton.setGravity(Gravity.CENTER);
        TextView powerCaption = text(
                "اتصال / فصل", 14, WHITE, true);
        powerCaption.setGravity(Gravity.CENTER);

        powerInner.addView(connectButton, lp(-1, dp(75)));
        powerInner.addView(powerCaption, lp(-1, dp(28)));
        power.addView(powerInner,
                new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams powerParams =
                lp(dp(155), dp(155));
        powerParams.gravity = Gravity.CENTER;
        powerParams.topMargin = dp(18);
        powerParams.bottomMargin = dp(10);
        connectionCard.addView(power, powerParams);

        power.setOnClickListener(v -> toggleConnection());

        section(body, "الكونفيج الحالي");

        LinearLayout configCard = card(body);
        configName = text(
                "لم يتم استيراد كونفيج", 15, WHITE, true);
        configName.setGravity(Gravity.CENTER);
        configCard.addView(configName, lp(-1, dp(42)));

        addLine(configCard,
                "يدعم هذا المحرك ملفات WireGuard بصيغة .conf");

        Button importButton = button("استيراد ملف الكونفيج");
        configCard.addView(importButton, lp(-1, dp(50)));
        importButton.setOnClickListener(v -> pickConfig());

        Button removeButton = button("حذف الكونفيج المحفوظ");
        removeButton.setBackground(shape(CARD, PURPLE, 13));
        LinearLayout.LayoutParams removeParams =
                lp(-1, dp(48));
        removeParams.topMargin = dp(8);
        configCard.addView(removeButton, removeParams);
        removeButton.setOnClickListener(v -> {
            if (vpnManager.isConnected()) {
                message("افصل الـVPN أولًا قبل حذف الكونفيج.");
                return;
            }
            prefs.edit().remove("config_text")
                    .remove("config_name").apply();
            refreshConfigName();
            message("تم حذف الكونفيج المحفوظ من التطبيق.");
        });

        section(body, "السيرفر");

        LinearLayout serverCard = card(body);
        addLine(serverCard,
                "السيرفر يُحدَّد من الكونفيج المستورد.");
        addLine(serverCard,
                "لا يوجد سيرفر افتراضي مضاف للتطبيق.");
        addLine(serverCard,
                "يمكنك استيراد كونفيج مختلف عند فصل الاتصال.");

        section(body, "حالة المحرك");

        LinearLayout engineCard = card(body);
        addLine(engineCard, "محرك WireGuard");
        addLine(engineCard, "الإصدار: " + vpnManager.getEngineVersion());

        Button refreshButton = button("تحديث حالة الاتصال");
        engineCard.addView(refreshButton, lp(-1, dp(48)));
        refreshButton.setOnClickListener(v -> refreshStatus());

        section(body, "القائمة الرئيسية");

        String[] items = {
                "الرئيسية",
                "السيرفرات والكونفيجات",
                "حالة الاتصال",
                "الإعدادات",
                "حول REX VPN"
        };

        for (String item : items) {
            TextView row = text("  " + item + "     ›", 15,
                    WHITE, true);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackground(shape(CARD, 0, 12));

            LinearLayout.LayoutParams rowParams =
                    lp(-1, dp(52));
            rowParams.bottomMargin = dp(8);
            body.addView(row, rowParams);

            row.setOnClickListener(v -> {
                switch (item) {
                    case "الرئيسية":
                        scroll.smoothScrollTo(0, 0);
                        break;
                    case "السيرفرات والكونفيجات":
                        scroll.smoothScrollTo(
                                0, configCard.getTop());
                        break;
                    case "حالة الاتصال":
                        refreshStatus();
                        scroll.smoothScrollTo(
                                0, connectionCard.getTop());
                        break;
                    case "الإعدادات":
                        message("إعدادات إضافية ستُضاف في المرحلة التالية.");
                        break;
                    case "حول REX VPN":
                        message("REX VPN\nعميل VPN يعتمد على WireGuard.");
                        break;
                }
            });
        }

        menu.setOnClickListener(v ->
                scroll.smoothScrollTo(0, body.getBottom()));

        refreshConfigName();
        refreshStatus();

        setContentView(root);
    }

    private void pickConfig() {
        if (vpnManager.isConnected()) {
            message("افصل الـVPN قبل تغيير الكونفيج.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CONFIG);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            waitingForPermission = false;
            return;
        }

        if (requestCode == REQUEST_CONFIG) {
            try (InputStream input = getContentResolver()
                    .openInputStream(data.getData())) {

                if (input == null) {
                    message("تعذر فتح الملف.");
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input,
                                java.nio.charset.StandardCharsets.UTF_8));

                StringBuilder content = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }

                String config = content.toString();

                if (!config.contains("[Interface]")
                        || !config.contains("[Peer]")) {
                    message("الملف لا يبدو كونفيج WireGuard صالحًا.");
                    return;
                }

                prefs.edit()
                        .putString("config_text", config)
                        .putString("config_name",
                                data.getData().getLastPathSegment())
                        .apply();

                refreshConfigName();
                message("تم حفظ الكونفيج. يمكنك تجربة الاتصال.");

            } catch (Exception e) {
                message("تعذرت قراءة الملف: " + e.getMessage());
            }

            return;
        }

        if (requestCode == REQUEST_VPN && waitingForPermission) {
            waitingForPermission = false;

            if (VpnService.prepare(this) == null) {
                connectUsingSavedConfig();
            } else {
                message("لم يتم منح إذن VPN.");
            }
        }
    }

    private void toggleConnection() {
        if (vpnManager.isConnected()) {
            status.setText("● جاري الفصل...");
            vpnManager.disconnect(new VpnManager.Callback() {
                @Override
                public void onSuccess(String message) {
                    runOnUiThread(() -> {
                        MainActivity.this.message(message);
                        refreshStatus();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() ->
                            MainActivity.this.message(message));
                }

                @Override
                public void onPermissionRequired() {
                    // لا يلزم إذن جديد للفصل.
                }
            });
            return;
        }

        String config = prefs.getString("config_text", null);
        if (config == null || config.trim().isEmpty()) {
            message("استورد كونفيج WireGuard أولًا.");
            pickConfig();
            return;
        }

        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            waitingForPermission = true;
            startActivityForResult(permissionIntent, REQUEST_VPN);
        } else {
            connectUsingSavedConfig();
        }
    }

    private void connectUsingSavedConfig() {
        String config = prefs.getString("config_text", null);

        if (config == null || config.trim().isEmpty()) {
            message("لا يوجد كونفيج محفوظ.");
            return;
        }

        status.setText("● جاري الاتصال...");
        detail.setText("المحرك يحاول تشغيل نفق WireGuard.");

        vpnManager.connect(config, new VpnManager.Callback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    MainActivity.this.message(message);
                    refreshStatus();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    MainActivity.this.message(message);
                    refreshStatus();
                });
            }

            @Override
            public void onPermissionRequired() {
                runOnUiThread(() -> {
                    Intent intent = VpnService.prepare(MainActivity.this);
                    if (intent != null) {
                        waitingForPermission = true;
                        startActivityForResult(intent, REQUEST_VPN);
                    }
                });
            }
        });
    }

    private void refreshConfigName() {
        if (configName == null) return;

        String name = prefs.getString(
                "config_name", null);

        if (name == null || name.trim().isEmpty()) {
            configName.setText("لم يتم استيراد كونفيج");
        } else {
            configName.setText(name);
        }
    }

    private void refreshStatus() {
        if (status == null) return;

        boolean connected = vpnManager.isConnected();

        if (connected) {
            status.setText("● النفق يعمل");
            status.setTextColor(CYAN);
            detail.setText(
                    "محرك WireGuard يؤكد أن حالة النفق UP.");
            connectButton.setText("⏻");
        } else {
            status.setText("● غير متصل");
            status.setTextColor(MUTED);
            detail.setText(
                    "الإنترنت غير محمي بواسطة نفق VPN نشط.");
            connectButton.setText("⏻");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (vpnManager != null) {
            refreshStatus();
            refreshConfigName();
        }
    }
}
