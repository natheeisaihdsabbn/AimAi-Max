package com.aimai.max;

import android.app.Activity;
import com.aimai.max.assistant.ScreenCaptureService;
import android.media.projection.MediaProjectionManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int SCREEN_CAPTURE_REQUEST = 2002;
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


    private void requestScreenCapturePermission() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                manager.createScreenCaptureIntent(),
                SCREEN_CAPTURE_REQUEST
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Intent captureIntent = new Intent(this, ScreenCaptureService.class);
                captureIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                captureIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(captureIntent);
                } else {
                    startService(captureIntent);
                }

                Intent gameIntent = getPackageManager()
                        .getLaunchIntentForPackage("com.miniclip.carrom");
                if (gameIntent != null) {
                    gameIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(gameIntent);
                } else {
                    openMessage("Carrom Disc Pool was not found. Please install the game first.");
                }
            } else {
                Toast.makeText(this, "Screen capture permission was denied.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyKeepScreenAwake() {
        boolean enabled = getSharedPreferences("aimai_settings", MODE_PRIVATE)
                .getBoolean("keep_screen_awake", false);
        if (enabled) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyKeepScreenAwake();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
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
        launch.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !Settings.canDrawOverlays(this)) {
                Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(permissionIntent);
                openMessage("Allow AimAi Max to display over other apps, then tap Launch again.");
                return;
            }

            Intent assistantIntent = new Intent(
                this,
                com.aimai.max.assistant.AssistantOverlayService.class
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(assistantIntent);
            } else {
                startService(assistantIntent);
            }

            requestScreenCapturePermission();


        });
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

        android.widget.LinearLayout appContainer =
                new android.widget.LinearLayout(this);
        appContainer.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.FrameLayout screenHost =
                new android.widget.FrameLayout(this);

        android.widget.LinearLayout.LayoutParams contentParams =
                new android.widget.LinearLayout.LayoutParams(
                        -1, 0, 1f);

        appContainer.addView(screenHost, contentParams);
        screenHost.addView(scroll);

        android.widget.LinearLayout navigation =
                new android.widget.LinearLayout(this);
        navigation.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        navigation.setBackgroundColor(Color.rgb(20, 24, 36));

        String[] screens = {
                "Home", "Friends", "Equipment", "Events", "Shop", "Settings"
        };

        for (String screen : screens) {
            android.widget.TextView button = new android.widget.TextView(this);
            button.setText(screen);
            button.setTextColor(Color.WHITE);
            button.setTextSize(10);
            button.setGravity(android.view.Gravity.CENTER);
            button.setPadding(3, 16, 3, 16);
            button.setOnClickListener(v -> {
                screenHost.removeAllViews();
                View page = screen.equals("Home")
                        ? scroll
                        : com.aimai.max.ui.FeatureScreens.create(this, screen);
                screenHost.addView(page);
            });

            navigation.addView(button,
                    new android.widget.LinearLayout.LayoutParams(
                            0, -2, 1f));
        }

        appContainer.addView(navigation);
        setContentView(appContainer);
    }
}
