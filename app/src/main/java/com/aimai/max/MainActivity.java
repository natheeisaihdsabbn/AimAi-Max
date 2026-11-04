package com.aimai.max;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.aimai.max.assistant.AssistantOverlayService;
import com.aimai.max.assistant.ScreenCaptureService;

public class MainActivity extends Activity {
    private static final int SCREEN_CAPTURE_REQUEST = 2002;
    private final int bg = Color.rgb(12, 14, 20);
    private final int card = Color.rgb(25, 29, 39);
    private final int gold = Color.rgb(225, 181, 75);
    private FrameLayout host;
    private LinearLayout navigation;
    private String current = "Home";

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(22), dp(24), dp(22), dp(24));
        return l;
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private void launchGame() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            new AlertDialog.Builder(this).setMessage(
                    "Allow display over other apps, then return and tap Launch.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Intent assistant = new Intent(this, AssistantOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(assistant); else startService(assistant);
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SCREEN_CAPTURE_REQUEST) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent capture = new Intent(this, ScreenCaptureService.class);
            capture.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
            capture.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(capture); else startService(capture);
            Intent game = getPackageManager().getLaunchIntentForPackage("com.miniclip.carrom");
            if (game != null) startActivity(game);
            else new AlertDialog.Builder(this).setMessage(
                    "Carrom Disc Pool was not found. Install the game first.")
                    .setPositiveButton("OK", null).show();
        } else {
            Toast.makeText(this, "Screen capture permission was denied.", Toast.LENGTH_LONG).show();
        }
    }
    private void show(String page) {
        current = page; host.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column();
        body.setBackgroundColor(bg);
        if ("Home".equals(page)) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.aimai_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoP = new LinearLayout.LayoutParams(dp(92), dp(92));
            logoP.gravity = Gravity.CENTER; body.addView(logo, logoP);
            TextView title = text("AimAi Max", 30, gold, true);
            title.setGravity(Gravity.CENTER); body.addView(title);
            TextView sub = text("Think ahead. Finish faster.", 15, Color.LTGRAY, false);
            sub.setGravity(Gravity.CENTER); body.addView(sub);
            addSpace(body, 28);
            body.addView(text("GAMES", 21, Color.WHITE, true));
            LinearLayout game = card();
            game.addView(text("CARROM DISC POOL", 22, Color.WHITE, true));
            game.addView(text("Carrom Assistant", 15, Color.LTGRAY, false));
            Button launch = actionButton("Launch");
            launch.setOnClickListener(v -> launchGame());
            game.addView(launch);
            body.addView(game);
            addSpace(body, 18);
            body.addView(text("ASSISTANT", 18, Color.WHITE, true));
            body.addView(text("Your floating assistant opens inside the game. Tap its bubble to access Drawings, Skins and System settings.", 14, Color.LTGRAY, false));
        } else if ("Shop".equals(page) || "Sellers".equals(page)) {
            body.addView(text("SHOP", 25, gold, true));
            body.addView(text("Themes and guide styles", 15, Color.LTGRAY, false));
            addSpace(body, 18);
            for (String item : new String[]{"Classic Theme", "Neon Theme", "Gold Theme"}) {
                LinearLayout row = card();
                row.addView(text(item, 17, Color.WHITE, true));
                row.addView(text("Visual theme preview", 13, Color.LTGRAY, false));
                body.addView(row);
            }
        } else {
            body.addView(text("SETTINGS", 25, gold, true));
            android.content.SharedPreferences prefs = getSharedPreferences("aimai_settings", MODE_PRIVATE);
            Switch awake = new Switch(this); awake.setText("Keep screen awake");
            awake.setTextColor(Color.WHITE); awake.setChecked(prefs.getBoolean("keep_screen_awake", false));
            awake.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("keep_screen_awake", checked).apply());
            body.addView(awake);
            body.addView(text("Assistant appearance and guide controls are available from the in-game floating bubble.", 14, Color.LTGRAY, false));
        }
        scroll.addView(body); host.addView(scroll);
        for (int i=0;i<navigation.getChildCount();i++) {
            TextView item=(TextView)navigation.getChildAt(i);
            item.setTextColor(item.getText().toString().equals(page) ? gold : Color.WHITE);
        }
    }
    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16)); l.setBackgroundColor(card);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(12), 0, dp(8)); l.setLayoutParams(p); return l;
    }
    private Button actionButton(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setTextSize(16);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31,36,49)));
        return b;
    }
    private void addSpace(LinearLayout l, int h) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        if (!LicenseManager.hasValidLocalLicense(this)) {
            startActivity(new Intent(this, LicenseActivity.class)); finish(); return;
        }
        LicenseManager.startPeriodicCheck(this);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        LinearLayout app = new LinearLayout(this); app.setOrientation(LinearLayout.VERTICAL); app.setBackgroundColor(bg);
        host = new FrameLayout(this); app.addView(host,new LinearLayout.LayoutParams(-1,0,1f));
        navigation = new LinearLayout(this); navigation.setBackgroundColor(Color.rgb(20,24,36));
        String[] tabs={"Home","Shop","Settings"};
        for (String tab:tabs) {
            TextView item=text(tab,14,Color.WHITE,false); item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5),dp(16),dp(5),dp(16));
            item.setOnClickListener(v->show(tab));
            navigation.addView(item,new LinearLayout.LayoutParams(0,-2,1f));
        }
        app.addView(navigation); setContentView(app); show("Home");
    }
    @Override protected void onResume() {
        super.onResume();
        if (getSharedPreferences("aimai_settings",MODE_PRIVATE).getBoolean("keep_screen_awake",false))
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}