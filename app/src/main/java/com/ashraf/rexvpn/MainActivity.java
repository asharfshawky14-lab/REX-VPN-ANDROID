package com.ashraf.rexvpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQUEST_VPN = 501;
    private static final int REQUEST_CONFIG = 502;

    private final int BG = Color.rgb(8, 8, 18);
    private final int CARD = Color.rgb(20, 19, 37);
    private final int PURPLE = Color.rgb(145, 75, 255);
    private final int CYAN = Color.rgb(0, 229, 255);
    private final int MUTED = Color.rgb(170, 170, 195);
    private final int WHITE = Color.WHITE;
    private final int GREEN = Color.rgb(53, 229, 154);
    private final int RED = Color.rgb(255, 69, 103);

    private VpnManager vpnManager;
    private SharedPreferences prefs;

    private FrameLayout pageHost;
    private View drawerOverlay;
    private LinearLayout drawerPanel;

    private View homePage;
    private View configPage;
    private View serversPage;
    private View settingsPage;
    private View aboutPage;

    private LinearLayout serversList;

    private TextView status;
    private TextView detail;
    private TextView configName;
    private TextView connectButton;
    private TextView configPageName;

    private boolean waitingForPermission = false;

    // --------------------------------------------------
    // أدوات التصميم
    // --------------------------------------------------

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(
            int color, int border, int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        if (border != 0) {
            drawable.setStroke(dp(1), border);
        }

        return drawable;
    }

    private TextView text(
            String value, int size, int color, boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private void message(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shape(CARD, Color.rgb(58, 45, 90), 18));

        LinearLayout.LayoutParams params = lp(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(card, params);

        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 19, WHITE, true);
        LinearLayout.LayoutParams params = lp(-1, dp(48));
        params.topMargin = dp(5);
        return title;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(PURPLE, 0, 13));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = button(value);
        button.setBackground(shape(CARD, PURPLE, 13));
        return button;
    }

    private void addLine(LinearLayout parent, String value) {
        TextView line = text(value, 14, MUTED, false);
        parent.addView(line, lp(-1, dp(35)));
    }

    private ScrollView makeScrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        return scroll;
    }

    private LinearLayout makePageBody(ScrollView scroll) {
        LinearLayout body = column();
        body.setPadding(dp(18), dp(10), dp(18), dp(26));
        scroll.addView(body);

        return body;
    }

    private void addPageHeading(
            LinearLayout body, String title, String subtitle) {

        TextView heading = text(title, 25, CYAN, true);
        body.addView(heading, lp(-1, dp(45)));

        TextView description = text(subtitle, 13, MUTED, false);
        description.setGravity(Gravity.TOP);
        description.setPadding(0, 0, 0, dp(14));
        body.addView(description, lp(-1, dp(48)));
    }

    // --------------------------------------------------
    // إنشاء الواجهة الرئيسية
    // --------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vpnManager = VpnManager.getInstance(this);
        prefs = getSharedPreferences("rex_settings", MODE_PRIVATE);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        buildApplication();
        refreshConfigName();
        refreshStatus();
        reloadServers();
    }

    private void buildApplication() {

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout shell = column();
        shell.setBackgroundColor(BG);

        // الرأس ثابت أثناء التنقل بين الصفحات.
        LinearLayout header = row();
        header.setPadding(dp(16), dp(9), dp(16), dp(9));
        header.setBackgroundColor(BG);

        LinearLayout brand = column();

        TextView logo = text("REX VPN", 25, CYAN, true);
        TextView tagline = text(
                "POWER BEHIND THE SHADOW", 9, MUTED, true);

        brand.addView(logo);
        brand.addView(tagline);

        header.addView(
                brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView menu = text("☰", 29, WHITE, true);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(shape(CARD, PURPLE, 14));

        header.addView(menu, lp(dp(50), dp(48)));

        shell.addView(header, lp(-1, -2));

        pageHost = new FrameLayout(this);
        shell.addView(pageHost,
                new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        buildHomePage();
        buildConfigPage();
        buildServersPage();
        buildSettingsPage();
        buildAboutPage();

        // طبقة التعتيم عند فتح القائمة.
        drawerOverlay = new View(this);
        drawerOverlay.setBackgroundColor(0xAA000000);
        drawerOverlay.setVisibility(View.GONE);

        root.addView(drawerOverlay, new FrameLayout.LayoutParams(
                -1, -1));

        drawerOverlay.setOnClickListener(v -> closeDrawer());

        // القائمة الجانبية.
        drawerPanel = column();
        drawerPanel.setBackground(shape(
                Color.rgb(14, 12, 29), PURPLE, 18));
        drawerPanel.setPadding(
                dp(16), dp(20), dp(16), dp(18));

        FrameLayout.LayoutParams drawerParams =
                new FrameLayout.LayoutParams(
                        dp(290), -1, Gravity.RIGHT | Gravity.TOP);

        drawerParams.setMargins(
                dp(8), dp(8), dp(8), dp(8));

        drawerPanel.setVisibility(View.GONE);
        root.addView(drawerPanel, drawerParams);

        buildDrawer();

        menu.setOnClickListener(v -> openDrawer());

        setContentView(root);
        showPage("home");
    }

    // --------------------------------------------------
    // الصفحة الرئيسية
    // --------------------------------------------------

    private void buildHomePage() {

        ScrollView scroll = makeScrollPage();
        LinearLayout body = makePageBody(scroll);

        homePage = scroll;

        addPageHeading(body,
                "مرحبًا بك في REX",
                "تحكم في اتصالك من مكان واحد.");

        // صورة الذئب الأصلية.
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(shape(CARD, PURPLE, 22));
        hero.setClipToOutline(true);

        ImageView wolf = new ImageView(this);

        int wolfId = getResources().getIdentifier(
                "wolf_background",
                "drawable",
                getPackageName());

        if (wolfId != 0) {
            wolf.setImageResource(wolfId);
            wolf.setScaleType(ImageView.ScaleType.CENTER_CROP);

            hero.addView(wolf, new FrameLayout.LayoutParams(
                    -1, -1));
        } else {
            TextView fallback = text("🐺", 82, PURPLE, true);
            fallback.setGravity(Gravity.CENTER);

            hero.addView(fallback, new FrameLayout.LayoutParams(
                    -1, -1));
        }

        TextView heroCaption = text(
                "ENTER THE SHADOW", 16, WHITE, true);

        heroCaption.setGravity(Gravity.CENTER);
        heroCaption.setBackgroundColor(0x99080812);

        FrameLayout.LayoutParams captionParams =
                new FrameLayout.LayoutParams(
                        -1, dp(48), Gravity.BOTTOM);

        hero.addView(heroCaption, captionParams);

        LinearLayout.LayoutParams heroParams = lp(-1, dp(190));
        heroParams.bottomMargin = dp(16);
        body.addView(hero, heroParams);

        // بطاقة الاتصال.
        LinearLayout connectionCard = card(body);

        status = text("● غير متصل", 19, MUTED, true);
        status.setGravity(Gravity.CENTER);

        connectionCard.addView(status, lp(-1, dp(38)));

        detail = text(
                "لا يوجد نفق VPN نشط حاليًا.",
                12, MUTED, false);

        detail.setGravity(Gravity.CENTER);
        connectionCard.addView(detail, lp(-1, dp(43)));

        // شريط نبض متحرك.
        LinearLayout pulse = row();
        pulse.setGravity(Gravity.CENTER);
        pulse.setPadding(0, dp(6), 0, dp(8));

        int[] heights = {12, 23, 34, 20, 38, 18, 30, 14, 25};

        for (int i = 0; i < heights.length; i++) {
            View bar = new View(this);

            bar.setBackground(shape(
                    i % 2 == 0 ? CYAN : PURPLE, 0, 4));

            LinearLayout.LayoutParams barParams =
                    lp(dp(5), dp(heights[i]));

            barParams.setMargins(dp(3), 0, dp(3), 0);
            pulse.addView(bar, barParams);

            ScaleAnimation animation = new ScaleAnimation(
                    1f, 1f,
                    0.35f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);

            animation.setDuration(420 + (i * 65));
            animation.setRepeatCount(Animation.INFINITE);
            animation.setRepeatMode(Animation.REVERSE);
            animation.setStartOffset(i * 85L);

            bar.startAnimation(animation);
        }

        connectionCard.addView(pulse, lp(-1, dp(48)));

        FrameLayout power = new FrameLayout(this);
        power.setBackground(shape(
                Color.rgb(39, 22, 68), PURPLE, 100));

        LinearLayout powerInner = column();
        powerInner.setGravity(Gravity.CENTER);

        connectButton = text("⏻", 51, CYAN, true);
        connectButton.setGravity(Gravity.CENTER);

        TextView powerCaption = text(
                "اتصال / فصل", 13, WHITE, true);

        powerCaption.setGravity(Gravity.CENTER);

        powerInner.addView(connectButton, lp(-1, dp(70)));
        powerInner.addView(powerCaption, lp(-1, dp(28)));

        power.addView(powerInner,
                new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams powerParams =
                lp(dp(145), dp(145));

        powerParams.gravity = Gravity.CENTER;
        powerParams.topMargin = dp(8);
        powerParams.bottomMargin = dp(12);

        connectionCard.addView(power, powerParams);

        power.setOnClickListener(v -> toggleConnection());

        // ملخص الكونفيج.
        body.addView(sectionTitle("الكونفيج الحالي"));

        LinearLayout currentConfigCard = card(body);

        configName = text(
                "لم يتم استيراد كونفيج", 15, WHITE, true);

        currentConfigCard.addView(configName, lp(-1, dp(42)));

        addLine(currentConfigCard,
                "محرك الاتصال: WireGuard");

        Button configsButton = button("فتح الكونفيجات");
        currentConfigCard.addView(configsButton, lp(-1, dp(48)));

        configsButton.setOnClickListener(v -> showPage("configs"));

        // ملخص السيرفرات.
        body.addView(sectionTitle("السيرفرات"));

        LinearLayout serversCard = card(body);

        addLine(serversCard,
                "أضف بيانات السيرفرات التي تمتلكها.");

        Button serversButton = button("فتح قائمة السيرفرات");
        serversCard.addView(serversButton, lp(-1, dp(48)));

        serversButton.setOnClickListener(v -> {
            reloadServers();
            showPage("servers");
        });

        // المحرك.
        body.addView(sectionTitle("حالة المحرك"));

        LinearLayout engineCard = card(body);

        addLine(engineCard, "محرك WireGuard");
        addLine(engineCard,
                "الإصدار: " + vpnManager.getEngineVersion());

        Button refreshButton = outlineButton("تحديث حالة الاتصال");
        engineCard.addView(refreshButton, lp(-1, dp(48)));

        refreshButton.setOnClickListener(v -> refreshStatus());

        pageHost.addView(homePage);
    }

    // --------------------------------------------------
    // صفحة الكونفيجات
    // --------------------------------------------------

    private void buildConfigPage() {

        ScrollView scroll = makeScrollPage();
        LinearLayout body = makePageBody(scroll);

        configPage = scroll;

        addPageHeading(body,
                "الكونفيجات",
                "إدارة ملفات الاتصال المحفوظة على جهازك.");

        LinearLayout currentCard = card(body);

        addLine(currentCard, "الكونفيج المحفوظ حاليًا:");

        configPageName = text(
                "لم يتم استيراد كونفيج", 15, WHITE, true);

        currentCard.addView(configPageName, lp(-1, dp(48)));

        addLine(currentCard,
                "الصيغة المطلوبة: ملف WireGuard بصيغة .conf");

        Button importButton = button("＋ استيراد ملف كونفيج");

        currentCard.addView(importButton, lp(-1, dp(50)));
        importButton.setOnClickListener(v -> pickConfig());

        Button deleteButton = outlineButton("حذف الكونفيج المحفوظ");

        LinearLayout.LayoutParams deleteParams =
                lp(-1, dp(48));

        deleteParams.topMargin = dp(8);
        currentCard.addView(deleteButton, deleteParams);

        deleteButton.setOnClickListener(v -> deleteConfig());

        LinearLayout noteCard = card(body);

        TextView note = text(
                "معلومة مهمة\n\n"
                        + "استيراد الملف يحفظ إعداداته داخل التطبيق، "
                        + "لكنه لا يضمن أن السيرفر يعمل أو أن بيانات "
                        + "الاتصال صحيحة.",
                14, MUTED, false);

        note.setGravity(Gravity.TOP);
        note.setPadding(0, dp(4), 0, dp(8));

        noteCard.addView(note, lp(-1, -2));

        pageHost.addView(configPage);
    }

    private void deleteConfig() {

        if (vpnManager.isConnected()) {
            message("افصل الـVPN أولًا قبل حذف الكونفيج.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("حذف الكونفيج")
                .setMessage("هل تريد حذف الكونفيج المحفوظ؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (dialog, which) -> {
                    prefs.edit()
                            .remove("config_text")
                            .remove("config_name")
                            .apply();

                    refreshConfigName();
                    message("تم حذف الكونفيج المحفوظ.");
                })
                .show();
    }

    // --------------------------------------------------
    // صفحة السيرفرات
    // --------------------------------------------------

    private void buildServersPage() {

        ScrollView scroll = makeScrollPage();
        LinearLayout body = makePageBody(scroll);

        serversPage = scroll;

        addPageHeading(body,
                "السيرفرات",
                "قائمة مستقلة للسيرفرات التي ستضيفها لاحقًا.");

        LinearLayout introCard = card(body);

        TextView explanation = text(
                "مكانك لإدارة السيرفرات\n\n"
                        + "أضف اسم السيرفر وعنوانه عندما تتوفر "
                        + "لديك بيانات حقيقية.\n\n"
                        + "إضافة الاسم والعنوان وحدهما لا تنشئ "
                        + "اتصال VPN؛ ستحتاج أيضًا إلى إعدادات "
                        + "WireGuard الصالحة.",
                14, MUTED, false);

        explanation.setGravity(Gravity.TOP);
        introCard.addView(explanation, lp(-1, -2));

        Button addButton = button("＋ إضافة سيرفر");

        LinearLayout.LayoutParams addParams = lp(-1, dp(52));
        addParams.bottomMargin = dp(14);

        body.addView(addButton, addParams);
        addButton.setOnClickListener(v -> showAddServerDialog());

        TextView listHeading = text(
                "السيرفرات المحفوظة", 18, WHITE, true);

        body.addView(listHeading, lp(-1, dp(46)));

        serversList = column();
        body.addView(serversList, lp(-1, -2));

        pageHost.addView(serversPage);
    }

    private JSONArray getServers() {

        String saved = prefs.getString("rex_servers", "[]");

        try {
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveServers(JSONArray servers) {
        prefs.edit()
                .putString("rex_servers", servers.toString())
                .apply();
    }

    private void reloadServers() {

        if (serversList == null) {
            return;
        }

        serversList.removeAllViews();

        JSONArray servers = getServers();

        if (servers.length() == 0) {

            LinearLayout emptyCard = card(serversList);

            TextView emptyTitle = text(
                    "لا توجد سيرفرات مضافة", 17, CYAN, true);

            emptyTitle.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyTitle, lp(-1, dp(50)));

            TextView emptyText = text(
                    "القائمة جاهزة. أضف سيرفرًا عندما "
                            + "تتوفر لديك بياناته.",
                    13, MUTED, false);

            emptyText.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyText, lp(-1, dp(54)));

            return;
        }

        for (int i = 0; i < servers.length(); i++) {

            JSONObject server = servers.optJSONObject(i);

            if (server == null) {
                continue;
            }

            final String serverId = server.optString("id", "");
            String name = server.optString("name", "سيرفر");
            String address = server.optString("address", "");

            LinearLayout serverCard = card(serversList);

            TextView serverName = text(name, 17, WHITE, true);
            serverCard.addView(serverName, lp(-1, dp(38)));

            TextView serverAddress = text(
                    address, 13, CYAN, false);

            serverCard.addView(serverAddress, lp(-1, dp(34)));

            addLine(serverCard,
                    "الحالة: محفوظ في القائمة فقط");

            Button deleteButton = outlineButton("حذف السيرفر");

            serverCard.addView(deleteButton, lp(-1, dp(44)));

            deleteButton.setOnClickListener(v ->
                    confirmDeleteServer(serverId, name));
        }
    }

    private void showAddServerDialog() {

        LinearLayout form = column();
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(WHITE);
        nameInput.setHintTextColor(MUTED);
        nameInput.setHint("اسم السيرفر");
        nameInput.setTextSize(15);
        nameInput.setBackground(shape(CARD, PURPLE, 12));
        nameInput.setPadding(dp(12), dp(4), dp(12), dp(4));

        form.addView(nameInput, lp(-1, dp(52)));

        EditText addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setTextColor(WHITE);
        addressInput.setHintTextColor(MUTED);
        addressInput.setHint("العنوان أو IP والمنفذ");
        addressInput.setTextSize(15);
        addressInput.setBackground(shape(CARD, PURPLE, 12));
        addressInput.setPadding(dp(12), dp(4), dp(12), dp(4));

        LinearLayout.LayoutParams addressParams =
                lp(-1, dp(52));

        addressParams.topMargin = dp(12);
        form.addView(addressInput, addressParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إضافة سيرفر")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String name = nameInput.getText()
                                .toString().trim();

                        String address = addressInput.getText()
                                .toString().trim();

                        if (name.isEmpty() || address.isEmpty()) {
                            message("اكتب اسم السيرفر وعنوانه.");
                            return;
                        }

                        JSONArray servers = getServers();

                        try {
                            JSONObject server = new JSONObject();

                            server.put("id",
                                    String.valueOf(System.currentTimeMillis()));

                            server.put("name", name);
                            server.put("address", address);

                            servers.put(server);
                            saveServers(servers);

                            reloadServers();

                            dialog.dismiss();

                            message("تم حفظ بيانات السيرفر في القائمة.");

                        } catch (Exception e) {
                            message("تعذر حفظ بيانات السيرفر.");
                        }
                    });
        });

        dialog.show();
    }

    private void confirmDeleteServer(
            String serverId, String serverName) {

        new AlertDialog.Builder(this)
                .setTitle("حذف السيرفر")
                .setMessage("هل تريد حذف " + serverName + " من القائمة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (dialog, which) -> {

                    JSONArray oldServers = getServers();
                    JSONArray newServers = new JSONArray();

                    for (int i = 0; i < oldServers.length(); i++) {

                        JSONObject server =
                                oldServers.optJSONObject(i);

                        if (server == null) {
                            continue;
                        }

                        if (!serverId.equals(
                                server.optString("id", ""))) {
                            newServers.put(server);
                        }
                    }

                    saveServers(newServers);
                    reloadServers();

                    message("تم حذف السيرفر من القائمة.");
                })
                .show();
    }

    // --------------------------------------------------
    // الإعدادات
    // --------------------------------------------------

    private void buildSettingsPage() {

        ScrollView scroll = makeScrollPage();
        LinearLayout body = makePageBody(scroll);

        settingsPage = scroll;

        addPageHeading(body,
                "الإعدادات",
                "أدوات إدارة التطبيق والاتصال.");

        LinearLayout connectionCard = card(body);

        TextView connectionTitle = text(
                "حالة الاتصال", 17, WHITE, true);

        connectionCard.addView(
                connectionTitle, lp(-1, dp(42)));

        Button refreshButton = button("تحديث الحالة");
        connectionCard.addView(refreshButton, lp(-1, dp(48)));

        refreshButton.setOnClickListener(v -> {
            refreshStatus();
            message("تم تحديث حالة الاتصال.");
        });

        LinearLayout storageCard = card(body);

        TextView storageTitle = text(
                "إدارة الكونفيج", 17, WHITE, true);

        storageCard.addView(storageTitle, lp(-1, dp(42)));

        Button openConfigs = outlineButton("إدارة الكونفيجات");
        storageCard.addView(openConfigs, lp(-1, dp(48)));

        openConfigs.setOnClickListener(v -> showPage("configs"));

        LinearLayout infoCard = card(body);

        TextView info = text(
                "يستخدم التطبيق محرك WireGuard "
                        + "الموجود في المشروع.\n\n"
                        + "لن يتم إنشاء اتصال ناجح إلا باستخدام "
                        + "إعدادات صحيحة وسيرفر يعمل.",
                14, MUTED, false);

        info.setGravity(Gravity.TOP);
        infoCard.addView(info, lp(-1, dp(100)));

        pageHost.addView(settingsPage);
    }

    // --------------------------------------------------
    // حول التطبيق
    // --------------------------------------------------

    private void buildAboutPage() {

        ScrollView scroll = makeScrollPage();
        LinearLayout body = makePageBody(scroll);

        aboutPage = scroll;

        addPageHeading(body,
                "حول REX VPN",
                "POWER BEHIND THE SHADOW");

        LinearLayout aboutCard = card(body);

        TextView appName = text(
                "REX VPN", 27, CYAN, true);

        appName.setGravity(Gravity.CENTER);
        aboutCard.addView(appName, lp(-1, dp(60)));

        TextView aboutText = text(
                "تطبيق لإدارة اتصال WireGuard.\n\n"
                        + "الإصدار: 1.0\n\n"
                        + "يعتمد الاتصال على ملف إعدادات "
                        + "WireGuard صالح وبيانات سيرفر حقيقية.\n\n"
                        + "هذا التطبيق لا يوفر سيرفرات تلقائيًا.",
                14, MUTED, false);

        aboutText.setGravity(Gravity.TOP);
        aboutCard.addView(aboutText, lp(-1, dp(180)));

        Button homeButton = button("العودة للرئيسية");
        body.addView(homeButton, lp(-1, dp(50)));

        homeButton.setOnClickListener(v -> showPage("home"));

        pageHost.addView(aboutPage);
    }

    // --------------------------------------------------
    // القائمة الجانبية
    // --------------------------------------------------

    private void buildDrawer() {

        TextView drawerTitle = text(
                "REX MENU", 22, CYAN, true);

        drawerPanel.addView(drawerTitle, lp(-1, dp(55)));

        TextView subtitle = text(
                "اختر الصفحة التي تريد فتحها",
                12, MUTED, false);

        drawerPanel.addView(subtitle, lp(-1, dp(35)));

        addDrawerItem("⌂   الرئيسية", "home");
        addDrawerItem("▤   الكونفيجات", "configs");
        addDrawerItem("◎   السيرفرات", "servers");
        addDrawerItem("⚙   الإعدادات", "settings");
        addDrawerItem("ⓘ   حول التطبيق", "about");

        View spacer = new View(this);

        drawerPanel.addView(spacer,
                new LinearLayout.LayoutParams(1, 0, 1));

        TextView footer = text(
                "REX VPN • WireGuard", 11, MUTED, false);

        drawerPanel.addView(footer, lp(-1, dp(38)));
    }

    private void addDrawerItem(String label, String page) {

        TextView item = text(label, 15, WHITE, true);
        item.setPadding(dp(12), 0, dp(12), 0);
        item.setBackground(shape(CARD, Color.rgb(48, 39, 76), 12));

        LinearLayout.LayoutParams params = lp(-1, dp(52));
        params.bottomMargin = dp(9);

        drawerPanel.addView(item, params);

        item.setOnClickListener(v -> showPage(page));
    }

    private void openDrawer() {
        drawerOverlay.setVisibility(View.VISIBLE);
        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.bringToFront();
        drawerOverlay.bringToFront();

        // إعادة وضع القائمة فوق طبقة التعتيم.
        drawerPanel.bringToFront();
    }

    private void closeDrawer() {
        if (drawerOverlay != null) {
            drawerOverlay.setVisibility(View.GONE);
        }

        if (drawerPanel != null) {
            drawerPanel.setVisibility(View.GONE);
        }
    }

    private void showPage(String page) {

        closeDrawer();

        homePage.setVisibility(View.GONE);
        configPage.setVisibility(View.GONE);
        serversPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        aboutPage.setVisibility(View.GONE);

        switch (page) {
            case "configs":
                configPage.setVisibility(View.VISIBLE);
                refreshConfigName();
                break;

            case "servers":
                serversPage.setVisibility(View.VISIBLE);
                reloadServers();
                break;

            case "settings":
                settingsPage.setVisibility(View.VISIBLE);
                refreshStatus();
                break;

            case "about":
                aboutPage.setVisibility(View.VISIBLE);
                break;

            default:
                homePage.setVisibility(View.VISIBLE);
                refreshStatus();
                refreshConfigName();
                break;
        }
    }

    // --------------------------------------------------
    // استيراد الكونفيج
    // --------------------------------------------------

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

    private String getDisplayName(Uri uri) {

        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    uri,
                    null,
                    null,
                    null,
                    null);

            if (cursor != null && cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);

                if (index >= 0) {
                    return cursor.getString(index);
                }
            }

        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String name = uri.getLastPathSegment();

        return name == null ? "WireGuard.conf" : name;
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        // معالجة إذن VPN أولًا؛ قد تكون data فارغة بصورة طبيعية.
        if (requestCode == REQUEST_VPN) {

            waitingForPermission = false;

            if (resultCode == RESULT_OK
                    && VpnService.prepare(this) == null) {

                connectUsingSavedConfig();

            } else {
                message("لم يتم منح إذن VPN.");
            }

            return;
        }

        if (requestCode != REQUEST_CONFIG) {
            return;
        }

        if (resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try (InputStream input =
                     getContentResolver().openInputStream(uri)) {

            if (input == null) {
                message("تعذر فتح الملف.");
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            input, StandardCharsets.UTF_8));

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
                    .putString("config_name", getDisplayName(uri))
                    .apply();

            refreshConfigName();

            message("تم حفظ الكونفيج. يمكنك تجربة الاتصال.");

        } catch (Exception e) {
            message("تعذرت قراءة الملف.");
        }
    }

    // --------------------------------------------------
    // الاتصال والفصل
    // --------------------------------------------------

    private void toggleConnection() {

        if (vpnManager.isConnected()) {

            status.setText("● جاري الفصل...");
            status.setTextColor(PURPLE);

            vpnManager.disconnect(new VpnManager.Callback() {

                @Override
                public void onSuccess(String result) {
                    runOnUiThread(() -> {
                        message(result);
                        refreshStatus();
                    });
                }

                @Override
                public void onError(String result) {
                    runOnUiThread(() -> {
                        message(result);
                        refreshStatus();
                    });
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
            showPage("configs");
            return;
        }

        Intent permissionIntent = VpnService.prepare(this);

        if (permissionIntent != null) {
            waitingForPermission = true;

            startActivityForResult(
                    permissionIntent, REQUEST_VPN);

        } else {
            connectUsingSavedConfig();
        }
    }

    private void connectUsingSavedConfig() {

        String config = prefs.getString("config_text", null);

        if (config == null || config.trim().isEmpty()) {
            message("لا يوجد كونفيج محفوظ.");
            showPage("configs");
            return;
        }

        status.setText("● جاري الاتصال...");
        status.setTextColor(PURPLE);

        detail.setText(
                "يحاول محرك WireGuard تشغيل نفق الاتصال.");

        vpnManager.connect(config, new VpnManager.Callback() {

            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    message(result);
                    refreshStatus();
                });
            }

            @Override
            public void onError(String result) {
                runOnUiThread(() -> {
                    message(result);
                    refreshStatus();
                });
            }

            @Override
            public void onPermissionRequired() {
                runOnUiThread(() -> {

                    Intent intent = VpnService.prepare(
                            MainActivity.this);

                    if (intent != null) {
                        waitingForPermission = true;

                        startActivityForResult(
                                intent, REQUEST_VPN);
                    }
                });
            }
        });
    }

    // --------------------------------------------------
    // تحديث الواجهة
    // --------------------------------------------------

    private void refreshConfigName() {

        String name = prefs.getString("config_name", null);

        if (name == null || name.trim().isEmpty()) {
            name = "لم يتم استيراد كونفيج";
        }

        if (configName != null) {
            configName.setText(name);
        }

        if (configPageName != null) {
            configPageName.setText(name);
        }
    }

    private void refreshStatus() {

        if (status == null || vpnManager == null) {
            return;
        }

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
                    "لا يوجد نفق VPN نشط حاليًا.");

            connectButton.setText("⏻");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (vpnManager != null) {
            refreshStatus();
            refreshConfigName();
            reloadServers();
        }
    }

    @Override
    public void onBackPressed() {

        if (drawerPanel != null
                && drawerPanel.getVisibility() == View.VISIBLE) {

            closeDrawer();
            return;
        }

        if (homePage != null
                && homePage.getVisibility() != View.VISIBLE) {

            showPage("home");
            return;
        }

        super.onBackPressed();
    }
}
