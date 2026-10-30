package com.aimai.max;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    int bg = Color.rgb(12, 14, 20);
    int panel = Color.rgb(25, 29, 39);
    int gold = Color.rgb(225, 181, 75);

    LinearLayout root;

    TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        return b;
    }

    void section(String title) {
        TextView t = text(title, 19, Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(4, 20, 4, 10);
        root.addView(t);
    }

    void openMessage(String message) {
        new AlertDialog.Builder(this)
            .setTitle("AimAi Max")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!LicenseManager.hasValidLocalLicense(this)) {
            startActivity(new android.content.Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        LicenseManager.startPeriodicCheck(this);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 20, 18, 24);
        root.setBackgroundColor(bg);
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aimai_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(150, 110);
        lp.gravity = Gravity.CENTER;
        logo.setLayoutParams(lp);
        root.addView(logo);

        TextView heading = text("AimAi Max", 28, gold);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);

        TextView subtitle = text("Think ahead. Finish faster.", 14, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        section("GAMES");

        LinearLayout game = new LinearLayout(this);
        game.setOrientation(LinearLayout.VERTICAL);
        game.setPadding(16, 18, 16, 18);
        game.setBackgroundTintList(null);
        game.setBackgroundColor(panel);

        game.addView(text("CARROM DISC POOL", 20, Color.WHITE));
        game.addView(text("Carrom Assistant", 14, Color.LTGRAY));

        Button launch = button("Launch");
        launch.setOnClickListener(v ->
            openMessage("Carrom Disc Pool launch integration will be configured next.")
        );
        game.addView(launch);
        root.addView(game);

        section("ASSISTANT");
        String[] features = {
            "DRAWINGS", "VISIBLE SEARCH", "AUTOMATION",
            "FAST / HUMAN MODE", "AUTO QUEUE"
        };

        for (String feature : features) {
            Button b = button(feature);
            b.setOnClickListener(v ->
                openMessage(feature + " settings will be added in the next development step.")
            );
            root.addView(b);
        }

        section("MENU");
        String[] menu = {"HOME", "FRIENDS", "EQUIPMENT", "EVENTS", "SHOP", "SETTINGS"};
        for (String item : menu) {
            Button b = button(item);
            b.setOnClickListener(v ->
                openMessage(item + " screen is planned for the next step.")
            );
            root.addView(b);
        }

        setContentView(scroll);
    }
}
