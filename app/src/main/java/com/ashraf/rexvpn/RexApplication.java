package com.ashraf.rexvpn;

import android.app.Application;
import android.os.Build;

import java.util.Locale;

import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.SetupOptions;

public class RexApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Libbox.setLocale(Locale.getDefault().toLanguageTag());

            SetupOptions options = new SetupOptions();
            options.setBasePath(getFilesDir().getAbsolutePath());
            options.setWorkingPath(getFilesDir().getAbsolutePath());
            options.setTempPath(getCacheDir().getAbsolutePath());
            options.setFixAndroidStack(true);
            options.setLogMaxLines(3000);
            options.setDebug(BuildConfig.DEBUG);
            options.setAppVersion(BuildConfig.VERSION_NAME);
            options.setAppMarketingVersion(BuildConfig.VERSION_NAME);
            options.setCrashReportSource("REX VPN");

            Libbox.setup(options);

        } catch (Exception e) {
            android.util.Log.e("REX-VPN", "Libbox initialization failed", e);
        }
    }
}
