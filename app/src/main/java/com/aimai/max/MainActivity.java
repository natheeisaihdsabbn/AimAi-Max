package com.aimai.max;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aimai.max.assistant.AssistantOverlayService;
import com.aimai.max.assistant.ScreenCaptureService;
import com.aimai.max.ui.FeatureScreens;

public class MainActivity extends Activity {
    private static final int SCREEN_CAPTURE_REQUEST = 2002;
    private final int background = Color.rgb(10, 13, 19);
    private final int surface = Color.rgb(23, 29, 39);
    private final int accent = Color.rgb(0, 214, 190);
    private FrameLayout screenHost;
    private View homePage;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private void showPage(String page) {
        screenHost.removeAllViews();
        View content = "Home".equals(page) ? homePage : FeatureScreens.create(this, page);
        screenHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openAssistant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this, "Allow display over other apps, then tap Launch again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent overlay = new Intent(this, AssistantOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(overlay);
        else startService(overlay);
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!LicenseManager.hasValidLocalLicense(this)) {
            startActivity(new Intent(this, LicenseActivity.class));
            finish();
            return;
        }
        LicenseManager.startPeriodicCheck(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout app = new LinearLayout(this);
        app.setOrientation(LinearLayout.VERTICAL);
        app.setBackgroundColor(background);
        screenHost = new FrameLayout(this);
        app.addView(screenHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        homePage = buildHome();
        showPage("Home");

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(17, 22, 30));
        String[] pages = {"Home", "Shop", "Settings"};
        String[] icons = {"⌂", "◇", "⚙"};
        for (int i = 0; i < pages.length; i++) {
            final String page = pages[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView icon = label(icons[i], 20, accent, true);
            icon.setGravity(Gravity.CENTER);
            TextView name = label(page, 11, Color.WHITE, false);
            name.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(name);
            item.setPadding(0, dp(9), 0, dp(8));
            item.setOnClickListener(v -> showPage(page));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        app.addView(nav);
        setContentView(app);
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(28));
        scroll.addView(content);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aimai_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, params(54, 54));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(label("AimAi Max", 22, Color.WHITE, true));
        brand.addView(label("CARROM ASSISTANT", 10, accent, true));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(header);

        TextView welcome = label("Ready when you are.", 25, Color.WHITE, true);
        welcome.setPadding(0, dp(30), 0, dp(5));
        content.addView(welcome);
        TextView subtitle = label("Your in-game visual assistant.", 14, Color.LTGRAY, false);
        content.addView(subtitle);

        LinearLayout gameCard = new LinearLayout(this);
        gameCard.setOrientation(LinearLayout.VERTICAL);
        gameCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        gameCard.setBackground(rounded(surface, 20));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(26);
        content.addView(gameCard, cardParams);

        TextView tag = label("SUPPORTED GAME", 10, accent, true);
        gameCard.addView(tag);
        TextView gameName = label("Carrom Disc Pool", 22, Color.WHITE, true);
        gameName.setPadding(0, dp(8), 0, dp(4));
        gameCard.addView(gameName);
        gameCard.addView(label("Live board analysis and user-controlled shot guidance.",
                13, Color.LTGRAY, false));

        Button launch = new Button(this);
        launch.setText("Launch Assistant");
        launch.setAllCaps(false);
        launch.setTextColor(Color.rgb(5, 18, 20));
        launch.setTypeface(null, Typeface.BOLD);
        launch.setBackground(rounded(accent, 14));
        LinearLayout.LayoutParams launchParams = params(-1, 52);
        launchParams.topMargin = dp(20);
        gameCard.addView(launch, launchParams);
        launch.setOnClickListener(v -> openAssistant());

        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(16), dp(15), dp(16), dp(15));
        note.setBackground(rounded(Color.rgb(18, 35, 39), 16));
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(16);
        content.addView(note, noteParams);
        note.addView(label("HOW IT WORKS", 11, accent, true));
        TextView instructions = label("1. Launch the assistant.\n2. Allow screen capture.\n3. Open Carrom Disc Pool and use the floating guide.",
                13, Color.LTGRAY, false);
        instructions.setLineSpacing(dp(5), 1f);
        instructions.setPadding(0, dp(8), 0, 0);
        note.addView(instructions);

        return scroll;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SCREEN_CAPTURE_REQUEST) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture permission was not granted.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent capture = new Intent(this, ScreenCaptureService.class);
        capture.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        capture.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(capture);
        else startService(capture);

        Intent game = getPackageManager().getLaunchIntentForPackage("com.miniclip.carrom");
        if (game != null) {
            game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(game);
        } else {
            Toast.makeText(this, "Carrom Disc Pool was not found. Please install it first.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean keepAwake = getSharedPreferences("aimai_settings", MODE_PRIVATE)
                .getBoolean("keep_screen_awake", false);
        if (keepAwake) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}