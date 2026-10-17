package com.ashraf.rexvpn;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.util.Locale;

import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.SetupOptions;

public class RexApplication extends Application {

    private static final String TAG = "REX-VPN";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            File baseDir = getFilesDir();
            File workingDir = new File(baseDir, "singbox");
            File tempDir = getCacheDir();

            if (!baseDir.exists() && !baseDir.mkdirs()) {
                throw new IllegalStateException(
                        "Cannot create base directory"
                );
            }

            if (!workingDir.exists() && !workingDir.mkdirs()) {
                throw new IllegalStateException(
                        "Cannot create Sing-box working directory"
                );
            }

            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new IllegalStateException(
                        "Cannot create temporary directory"
                );
            }

            Libbox.setLocale(
                    Locale.getDefault().toLanguageTag()
            );

            SetupOptions options = new SetupOptions();

            options.setBasePath(baseDir.getAbsolutePath());
            options.setWorkingPath(workingDir.getAbsolutePath());
            options.setTempPath(tempDir.getAbsolutePath());

            options.setFixAndroidStack(true);
            options.setLogMaxLines(3000);
            options.setDebug(BuildConfig.DEBUG);

            options.setAppVersion(
                    String.valueOf(BuildConfig.VERSION_CODE)
            );

            options.setAppMarketingVersion(
                    BuildConfig.VERSION_NAME
            );

            options.setCrashReportSource("REX VPN");

            Libbox.setup(options);

            Log.i(TAG, "Sing-box initialized successfully");

        } catch (Exception | LinkageError e) {
            Log.e(
                    TAG,
                    "Sing-box initialization failed",
                    e
            );
        }
    }
}
