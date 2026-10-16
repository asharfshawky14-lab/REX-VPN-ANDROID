package com.ashraf.rexvpn;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

public class MainActivity extends Activity {

    private TextView status;
    private Button connectButton;
    private boolean connected = false;

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(
            int color, int strokeColor, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT) {
            bg.setStroke(dp(1), strokeColor);
        }
        return bg;
    }

    private TextView text(
            String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(8, 8, 18));
        getWindow().setNavigationBarColor(Color.rgb(8, 8, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(8, 8, 18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView brand = text("REX", 28, Color.rgb(0, 229, 255), true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        header.addView(brand, new LinearLayout.LayoutParams(
                0, dp(52), 1));

        TextView menu = text("☰", 28, Color.WHITE, true);
        header.addView(menu, new LinearLayout.LayoutParams(
                dp(48), dp(52)));

        root.addView(header);

        TextView tagline = text(
                "THE POWER BEHIND THE SHADOW",
                10, Color.LTGRAY, true);
        root.addView(tagline);

        FrameLayout imageFrame = new FrameLayout(this);
        imageFrame.setBackground(shape(
                Color.rgb(18, 18, 34),
                Color.rgb(100, 40, 200), 22));

        ImageView wolf = new ImageView(this);
        int imageId = getResources().getIdentifier(
                "wolf_background", "drawable", getPackageName());

        if (imageId != 0) {
            wolf.setImageResource(imageId);
            wolf.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageFrame.addView(wolf, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            TextView fallback = text(
                    "🐺", 90, Color.rgb(155, 92, 255), true);
            imageFrame.addView(fallback,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        }

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        imageParams.topMargin = dp(18);
        imageParams.bottomMargin = dp(16);
        root.addView(imageFrame, imageParams);

        status = text("جاهز للاتصال", 18,
                Color.WHITE, true);
        root.addView(status);

        TextView description = text(
                "اتصالك يبدأ من هنا",
                13, Color.rgb(165, 165, 188), false);
        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(34));
        root.addView(description, descParams);

        connectButton = new Button(this);
        connectButton.setText("⏻   اتصال");
        connectButton.setTextSize(20);
        connectButton.setTextColor(Color.WHITE);
        connectButton.setAllCaps(false);
        connectButton.setBackground(shape(
                Color.rgb(100, 40, 200),
                Color.rgb(0, 229, 255), 18));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60));
        buttonParams.topMargin = dp(12);
        root.addView(connectButton, buttonParams);

        TextView note = text(
                "واجهة تجريبية — خدمة VPN لم تُفعّل بعد",
                11, Color.rgb(255, 200, 87), false);
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(40));
        noteParams.topMargin = dp(8);
        root.addView(note, noteParams);

        connectButton.setOnClickListener(v -> {
            status.setText("الواجهة تجريبية فقط");
            status.setTextColor(Color.rgb(255, 200, 87));
        });

        setContentView(root);
    }
}
