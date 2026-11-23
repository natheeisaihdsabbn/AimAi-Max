package com.aimai.max;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.os.PowerManager;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.aimai.max.assistant.AimAiGestureService;
import com.aimai.max.assistant.AssistantOverlayService;
import com.aimai.max.assistant.ScreenCaptureService;
import com.aimai.max.ui.BannerView;
import com.aimai.max.ui.BoardIconView;
import com.aimai.max.ui.IconView;
import com.aimai.max.ui.AimAiUiStyle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final int SCREEN_CAPTURE_REQUEST = 2002;
    private static final String[] TITLES = {"Home", "Sellers", "Leaderboard", "Settings"};
    private static final int[] NAV_ICONS = {IconView.HOME, IconView.PEOPLE, IconView.TROPHY, IconView.GEAR};

    /** Fill in real sellers here as {"@handle", "Region"}. Empty list shows "No sellers listed yet." */
    private static final String[][] SELLERS = {};

    private final int bg = Color.rgb(5, 6, 10);
    private final int card = Color.rgb(15, 17, 24);
    private final int line = Color.rgb(32, 35, 46);
    private final int purple = Color.rgb(111, 24, 157);
    private final int purpleLight = Color.rgb(195, 106, 255);
    private final int navPill = Color.rgb(63, 20, 84);
    private final int muted = Color.rgb(140, 145, 160);

    private FrameLayout host;
    private TextView title;
    private final IconView[] navIcons = new IconView[4];
    private final View[] navPills = new View[4];
    private View premiumPill;
    private int current = -1;

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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private GradientDrawable box(int color, int radius, int strokeColor) {
        GradientDrawable shape = rounded(color, radius);
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    // ------------------------------------------------------------------ assistant start

    private void startOverlayService() {
        Intent overlay = new Intent(this, AssistantOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(overlay);
        else startService(overlay);
    }

    private void launchGame() {
        Intent game = getPackageManager().getLaunchIntentForPackage("com.miniclip.carrom");
        if (game != null) {
            game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(game);
        } else {
            Toast.makeText(this, "Carrom Disc Pool was not found. Please install it first.",
                    Toast.LENGTH_LONG).show();
        }
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

        // Android 11+: scan with the accessibility screenshot, so there is no
        // "start recording or casting" question at all.
        if (Build.VERSION.SDK_INT >= 30) {
            if (!AimAiGestureService.isConnected()) {
                Toast.makeText(this,
                        "One time only: turn on AimAi Max in Accessibility. After that, Launch never asks for screen recording.",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            startOverlayService();
            AimAiGestureService.setScanEnabled(true);
            launchGame();
            return;
        }

        // Older Android: screen capture permission is the only way.
        startOverlayService();
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }

    private void stopAssistant() {
        AimAiGestureService.setScanEnabled(false);
        stopService(new Intent(this, AssistantOverlayService.class));
        stopService(new Intent(this, ScreenCaptureService.class));
        Toast.makeText(this, "Assistant stopped.", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ lifecycle

    // ------------------------------------------------------------------ permissions (all asked from inside the app)

    private boolean askedNotif = false, askedBattery = false, askedOverlay = false, askedAccess = false;
    private boolean grantChain = false;

    private boolean notifGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryOk() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean overlayOk() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private boolean accessOk() {
        return AimAiGestureService.isConnected();
    }

    private void askNotifications() {
        askedNotif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void askBattery() {
        askedBattery = true;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void askOverlay() {
        askedOverlay = true;
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void askAccessibility() {
        askedAccess = true;
        Toast.makeText(this, "Turn on AimAi Max, then come back.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    /** Asks for whatever is still missing, one permission at a time, each time the app comes back to the front. */
    private void grantNext() {
        if (!notifGranted() && !askedNotif) { askNotifications(); return; }
        if (!batteryOk() && !askedBattery) { askBattery(); return; }
        if (!overlayOk() && !askedOverlay) { askOverlay(); return; }
        if (!accessOk() && !askedAccess) { askAccessibility(); return; }
        grantChain = false;
        if (host != null && current == 0) showPage(0);
    }

    private View buildPermissionCard() {
        boolean n = notifGranted();
        boolean b = batteryOk();
        boolean o = overlayOk();
        boolean a = accessOk();
        if (n && b && o && a) return null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(box(card, 10, line));
        box.addView(label("Permissions", 13, Color.WHITE, true));
        addPermRow(box, "Notifications", n, v -> { askedNotif = false; askNotifications(); });
        addPermRow(box, "Run in background", b, v -> askBattery());
        addPermRow(box, "Display over other apps", o, v -> askOverlay());
        addPermRow(box, "Accessibility (Auto Play)", a, v -> askAccessibility());
        TextView all = label("Allow all", 12, Color.rgb(240, 238, 252), true);
        all.setGravity(Gravity.CENTER);
        AimAiUiStyle.surface(all, true);
        all.setOnClickListener(v -> {
            askedNotif = false;
            askedBattery = false;
            askedOverlay = false;
            askedAccess = false;
            grantChain = true;
            grantNext();
        });
        LinearLayout.LayoutParams ap = lp(-1, 34);
        ap.topMargin = dp(8);
        box.addView(all, ap);
        return box;
    }

    private void addPermRow(LinearLayout parent, String name, boolean on, View.OnClickListener ask) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(name, 12, Color.rgb(200, 204, 218), false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView state = label(on ? "On" : "Allow", 11,
                on ? Color.rgb(140, 210, 170) : Color.WHITE, false);
        state.setGravity(Gravity.CENTER);
        if (!on) {
            AimAiUiStyle.surface(state, false);
            state.setOnClickListener(ask);
        }
        row.addView(state, lp(on ? 40 : 60, 28));
        parent.addView(row, lp(-1, 34));
    }

    // ------------------------------------------------------------------ password (asked when Launch is tapped)

    private void launchWithPassword() {
        if (LicenseManager.hasValidLocalLicense(this)) {
            openAssistant();
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("Access key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Sign in")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String key = input.getText().toString().trim();
            if (key.isEmpty()) {
                input.setError("Enter your access key");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            LicenseManager.verify(this, key, result -> {
                if (isFinishing()) return;
                if (result == LicenseManager.Result.OK) {
                    dialog.dismiss();
                    LicenseManager.startPeriodicCheck(this);
                    openAssistant();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if (result == LicenseManager.Result.INVALID) input.setError("Invalid access key");
                else if (result == LicenseManager.Result.OTHER_DEVICE) input.setError("Already used on another phone");
                else input.setError("Cannot reach the server");
            });
        }));
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (LicenseManager.hasValidLocalLicense(this)) LicenseManager.startPeriodicCheck(this);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout app = new LinearLayout(this);
        app.setOrientation(LinearLayout.VERTICAL);
        app.setBackgroundColor(bg);
        app.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        app.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(line);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.leftMargin = dp(20);
        dividerParams.rightMargin = dp(20);
        app.addView(divider, dividerParams);

        host = new FrameLayout(this);
        app.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));
        app.addView(buildBottomBar(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(app);
        showPage(0);
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

        launchGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean keepAwake = getSharedPreferences("aimai_settings", MODE_PRIVATE)
                .getBoolean("keep_screen_awake", false);
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Refresh status rows (accessibility / overlay) after coming back from system settings.
        if (host != null && (current == 3 || current == 0)) showPage(current);
        if (grantChain) grantNext();
    }

    // ------------------------------------------------------------------ chrome

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aimai_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandBlock.addView(logo, lp(34, 34));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(10), 0, 0, 0);
        names.addView(label("AIM.AI", 18, Color.WHITE, true));
        names.addView(label("VERSION " + versionName(), 9, muted, false));
        brandBlock.addView(names);
        header.addView(brandBlock, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        title = label("AIM.AI", 1, Color.TRANSPARENT, false);
        View refresh = roundButton(IconView.REFRESH, v -> {
            if (current >= 0) showPage(current);
            Toast.makeText(this, "Screen refreshed", Toast.LENGTH_SHORT).show();
        });
        header.addView(refresh, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.END | Gravity.CENTER_VERTICAL));
        return header;
    }

    private View roundButton(int icon, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(box(card, 8, line));
        IconView iv = new IconView(this, icon);
        iv.setColor(Color.rgb(205, 208, 220));
        f.addView(iv, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        f.setOnClickListener(click);
        return f;
    }

    private View buildBottomBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(7), dp(22), dp(10));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(5), dp(6), dp(5));
        nav.setBackground(box(Color.rgb(14, 14, 21), 15, line));
        for (int i = 0; i < 4; i++) {
            final int index = i;
            FrameLayout cell = new FrameLayout(this);
            View pillBg = new View(this);
            pillBg.setBackground(rounded(navPill, 10));
            pillBg.setVisibility(View.INVISIBLE);
            cell.addView(pillBg, new FrameLayout.LayoutParams(dp(44), dp(38), Gravity.CENTER));
            IconView icon = new IconView(this, NAV_ICONS[i]);
            cell.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            navIcons[i] = icon;
            navPills[i] = pillBg;
            cell.setOnClickListener(v -> showPage(index));
            nav.addView(cell, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        wrap.addView(nav, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void showPage(int index) {
        current = index;
        for (int i = 0; i < 4; i++) {
            boolean on = i == index;
            navPills[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            navIcons[i].setColor(on ? purpleLight : muted);
        }
        host.removeAllViews();
        View page;
        switch (index) {
            case 1: page = buildSellers(); break;
            case 2: page = buildLeaderboard(); break;
            case 3: page = buildSettings(); break;
            default: page = buildGames(); break;
        }
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout pageColumn(ScrollView holder) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(16), dp(20), dp(20));
        holder.addView(column, new FrameLayout.LayoutParams(-1, -2));
        return column;
    }

    // ------------------------------------------------------------------ pages

    private View buildGames() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        column.addView(new BannerView(this), lp(-1, 156));
        LinearLayout bannerInfo = new LinearLayout(this);
        bannerInfo.setGravity(Gravity.CENTER_VERTICAL);
        bannerInfo.setPadding(dp(4), dp(15), dp(4), dp(4));
        bannerInfo.addView(label("Choose your game", 17, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        bannerInfo.addView(label("3 GAMES", 10, purpleLight, true));
        column.addView(bannerInfo);
        column.addView(gameListing("Carrom Pool", "Carrom Disc Pool", true), lp(-1, -2));
        column.addView(gameListing("8 Ball Pool", "8 Ball Pool", false), lp(-1, -2));
        column.addView(gameListing("Soccer Stars", "Soccer Stars", false), lp(-1, -2));
        TextView caveat = label("Aim and auto-play features in this build are for Carrom Disc Pool only.", 11, muted, false);
        caveat.setGravity(Gravity.CENTER);
        caveat.setPadding(dp(8), dp(14), dp(8), dp(4));
        column.addView(caveat, lp(-1, -2));
        return scroll;
    }

    private View gameListing(String name, String app, boolean supported) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(13), dp(13), dp(13));
        box.setBackground(box(card, 14, line));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View badge;
        if (supported) badge = new BoardIconView(this);
        else {
            TextView mark = label(name.equals("8 Ball Pool") ? "8" : "S", 24, purpleLight, true);
            mark.setGravity(Gravity.CENTER);
            mark.setBackground(box(Color.rgb(32, 19, 42), 12, Color.rgb(72, 37, 94)));
            badge = mark;
        }
        row.addView(badge, lp(52, 52));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, 0, 0);
        details.addView(label(name, 16, Color.WHITE, true));
        details.addView(label(supported ? "AimAi Max supported" : "Not integrated in this build", 11, muted, false));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chevron = label("···", 18, muted, true);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, lp(30, 34));
        chevron.setOnClickListener(v -> showMoreMenu(v));
        box.addView(row);
        TextView action = label(supported ? "Launch" : "Import", 13, Color.WHITE, true);
        action.setGravity(Gravity.CENTER);
        action.setBackground(rounded(purple, 9));
        LinearLayout.LayoutParams ap = lp(-1, 38); ap.topMargin = dp(12); box.addView(action, ap);
        action.setOnClickListener(v -> {
            if (supported) launchWithPassword();
            else new AlertDialog.Builder(this).setTitle(name)
                    .setMessage("This game is not integrated with AimAi Max yet. No game import or aiming feature is available in this build.")
                    .setPositiveButton("OK", null).show();
        });
        LinearLayout.LayoutParams cp = lp(-1, -2); cp.topMargin = dp(10); box.setLayoutParams(cp);
        return box;
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Accessibility settings");
        menu.getMenu().add(0, 2, 1, "Overlay permission");
        menu.getMenu().add(0, 3, 2, "Stop assistant");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                stopAssistant();
            }
            return true;
        });
        menu.show();
    }

    private View buildPass() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        SharedPreferences license = getSharedPreferences("aimai_license", MODE_PRIVATE);
        String key = license.getString("key", "");
        long verified = license.getLong("last_verified", 0L);

        FrameLayout hero = new FrameLayout(this);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(24, 25, 38), Color.rgb(12, 13, 20)});
        heroBg.setCornerRadius(dp(26));
        heroBg.setStroke(dp(1), line);
        hero.setBackground(heroBg);
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));
        LinearLayout heroColumn = new LinearLayout(this);
        heroColumn.setOrientation(LinearLayout.VERTICAL);
        TextView small = label("PREMIUM MEMBERSHIP", 10, muted, false);
        small.setLetterSpacing(0.15f);
        heroColumn.addView(small);
        TextView name = label("Premium Pass", 27, Color.WHITE, true);
        name.setPadding(0, dp(6), 0, dp(26));
        heroColumn.addView(name);
        heroColumn.addView(label(key.isEmpty() ? "Not active" : "Active", 40, Color.WHITE, true));
        TextView sub = label("LICENSE STATUS", 10, muted, false);
        sub.setLetterSpacing(0.15f);
        heroColumn.addView(sub);
        hero.addView(heroColumn, new FrameLayout.LayoutParams(-1, -2));
        TextView chip = label(key.isEmpty() ? "Locked" : "Active", 11, Color.rgb(140, 210, 170), false);
        chip.setPadding(dp(12), dp(4), dp(12), dp(4));
        chip.setBackground(box(Color.rgb(20, 36, 30), 14, Color.rgb(50, 90, 70)));
        hero.addView(chip, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        column.addView(hero, lp(-1, -2));

        TextView section = label("MEMBERSHIP DETAILS", 10, muted, false);
        section.setLetterSpacing(0.15f);
        section.setPadding(dp(4), dp(22), 0, dp(10));
        column.addView(section);

        LinearLayout detail = groupCard();
        String activated = verified > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(verified))
                : "—";
        addDetailRow(detail, "Activated", activated, false);
        addDetailRow(detail, "Expiry", "—", false);
        addDetailRow(detail, "Key ID", maskKey(key), false);
        addDetailRow(detail, "Seller", "—", true);
        column.addView(detail, lp(-1, -2));

        TextView signOut = label("Sign out", 15, Color.rgb(224, 128, 96), false);
        signOut.setGravity(Gravity.CENTER);
        signOut.setBackground(box(Color.TRANSPARENT, 8, Color.rgb(110, 60, 46)));
        signOut.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("This removes the license from this phone. You will need to enter your key again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (d, w) -> {
                    getSharedPreferences("aimai_license", MODE_PRIVATE).edit().clear().apply();
                    startActivity(new Intent(this, LicenseActivity.class));
                    finish();
                })
                .show());
        LinearLayout.LayoutParams sp = lp(-1, 40);
        sp.topMargin = dp(28);
        column.addView(signOut, sp);
        return scroll;
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "—";
        if (key.length() <= 8) return "••••";
        return key.substring(0, 4) + "••••" + key.substring(key.length() - 3);
    }

    private LinearLayout groupCard() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(box(card, 10, line));
        group.setPadding(dp(4), dp(2), dp(4), dp(2));
        return group;
    }

    private void addDetailRow(LinearLayout parent, String name, String value, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.addView(label(name, 15, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label(value, 13, muted, false));
        parent.addView(row, lp(-1, 54));
        if (!last) parent.addView(divider());
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(line);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.leftMargin = dp(14);
        p.rightMargin = dp(14);
        d.setLayoutParams(p);
        return d;
    }

    private View buildSellers() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        column.addView(label("Sellers", 22, Color.WHITE, true));
        TextView note = label("Search countries and regions", 13, muted, false);
        note.setPadding(0, dp(5), 0, dp(14)); column.addView(note);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(14); search.setTextColor(Color.WHITE);
        search.setHintTextColor(muted); search.setHint("Search country");
        search.setPadding(dp(15), 0, dp(15), 0); search.setBackground(box(card, 12, line));
        column.addView(search, lp(-1, 48));
        TextView unavailable = label("Seller listings are not connected in this build.", 12, muted, false);
        unavailable.setPadding(dp(2), dp(14), 0, dp(4)); column.addView(unavailable);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listp = lp(-1, -2); listp.topMargin = dp(5); column.addView(list, listp);
        String[] countries = {"Global", "Bangladesh", "India", "Pakistan", "Nepal", "Sri Lanka", "United States", "United Kingdom", "Indonesia", "Malaysia", "Saudi Arabia", "United Arab Emirates"};
        Runnable render = () -> {
            list.removeAllViews(); String q = search.getText().toString().trim().toLowerCase();
            for (String country : countries) if (country.toLowerCase().contains(q)) {
                LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), 0, dp(12), 0); row.setBackground(box(card, 10, line));
                TextView globe = label("◎", 20, purpleLight, true); globe.setGravity(Gravity.CENTER); row.addView(globe, lp(42, 42));
                TextView name = label(country, 14, Color.WHITE, false); name.setPadding(dp(12), 0, 0, 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(label("—", 13, muted, false));
                row.setOnClickListener(v -> Toast.makeText(this, "Seller listings are not available yet.", Toast.LENGTH_SHORT).show());
                LinearLayout.LayoutParams rp = lp(-1, 52); rp.topMargin = dp(8); list.addView(row, rp);
            }
        };
        render.run();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { render.run(); }
            public void afterTextChanged(Editable e) {}
        });
        return scroll;
    }

    private View buildLeaderboard() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        column.addView(label("Leaderboard", 22, Color.WHITE, true));
        LinearLayout empty = groupCard();
        empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(28), dp(20), dp(28));
        empty.addView(label("No leaderboard data yet", 16, Color.WHITE, true));
        TextView desc = label("A leaderboard service is not connected in this build.", 12, muted, false);
        desc.setGravity(Gravity.CENTER); desc.setPadding(0, dp(8), 0, 0); empty.addView(desc);
        LinearLayout.LayoutParams ep = lp(-1, -2); ep.topMargin = dp(16); column.addView(empty, ep);
        return scroll;
    }

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        SharedPreferences prefs = getSharedPreferences("aimai_settings", MODE_PRIVATE);
        sectionLabel(column, "APPEARANCE");
        LinearLayout appearance = groupCard();
        addSettingRow(appearance, IconView.INFO, "Theme", "Dark", null, true, v ->
                new AlertDialog.Builder(this).setTitle("Appearance").setMessage("Dark appearance is currently available.")
                        .setPositiveButton("OK", null).show());
        column.addView(appearance, lp(-1, -2));

        sectionLabel(column, "ACCOUNT");
        LinearLayout account = groupCard();
        addSettingRow(account, IconView.ACCESS, "Facebook login", "Not connected", null, false,
                v -> Toast.makeText(this, "Facebook login is not configured in this build.", Toast.LENGTH_SHORT).show());
        addSettingRow(account, IconView.INFO, "Language", "English", null, false,
                v -> new AlertDialog.Builder(this).setTitle("Language").setItems(new String[]{"English"}, (d, w) -> {})
                        .show());
        addSettingRow(account, IconView.PEOPLE, "Profile", "Profile 1", null, true, null);
        column.addView(account, lp(-1, -2));

        sectionLabel(column, "GOOGLE SERVICES");
        LinearLayout google = groupCard();
        addSettingRow(google, IconView.CHIP, "Google services", "Not configured", null, true,
                v -> Toast.makeText(this, "Google services are not configured.", Toast.LENGTH_SHORT).show());
        column.addView(google, lp(-1, -2));

        sectionLabel(column, "DIAGNOSTICS");
        LinearLayout diagnostics = groupCard();
        Switch save = new Switch(this); save.setChecked(prefs.getBoolean("save_diagnostics", false));
        save.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("save_diagnostics", checked).apply());
        addSettingRow(diagnostics, IconView.DOC, "Save diagnostics", null, save, true, null);
        column.addView(diagnostics, lp(-1, -2));

        sectionLabel(column, "COMMUNITY");
        LinearLayout social = groupCard();
        addSettingRow(social, IconView.INFO, "Website", "Not set", null, false, null);
        addSettingRow(social, IconView.CHAT, "Telegram", "Not set", null, true, null);
        column.addView(social, lp(-1, -2));

        sectionLabel(column, "LOADER");
        LinearLayout loader = groupCard();
        addSettingRow(loader, IconView.REFRESH, "Loader update", "Check", null, true,
                v -> new AlertDialog.Builder(this).setTitle("Loader update")
                        .setMessage("No loader update service is configured for this build.")
                        .setPositiveButton("OK", null).show());
        column.addView(loader, lp(-1, -2));

        sectionLabel(column, "ASSISTANT & DEVICE");
        LinearLayout assistant = groupCard();
        boolean accessibility = AimAiGestureService.isConnected();
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        addSettingRow(assistant, IconView.ACCESS, "Accessibility (Auto Play)", accessibility ? "On" : "Off", null, false,
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addSettingRow(assistant, IconView.LAYERS, "Display over other apps", overlay ? "On" : "Off", null, false,
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        Switch awake = new Switch(this); awake.setChecked(prefs.getBoolean("keep_screen_awake", false));
        awake.setOnCheckedChangeListener((b, checked) -> { prefs.edit().putBoolean("keep_screen_awake", checked).apply();
            if (checked) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        addSettingRow(assistant, IconView.CHIP, "Keep screen awake", null, awake, true, null);
        column.addView(assistant, lp(-1, -2));

        sectionLabel(column, "ABOUT");
        LinearLayout about = groupCard();
        addSettingRow(about, IconView.DOC, "Version", versionName(), null, false, null);
        addSettingRow(about, IconView.CHIP, "Build", versionCode(), null, false, null);
        addSettingRow(about, IconView.CHIP, "Architecture", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toUpperCase() : "—", null, false, null);
        addSettingRow(about, IconView.INFO, "Legal", "›", null, true, v -> showLegal());
        column.addView(about, lp(-1, -2));
        LinearLayout logs = groupCard();
        addSettingRow(logs, IconView.SHARE, "Share logs", "›", null, false, v -> shareLogs());
        addSettingRow(logs, IconView.TRASH, "Clear logs", "›", null, true, v -> clearLogs());
        LinearLayout.LayoutParams logp = lp(-1, -2); logp.topMargin = dp(12); column.addView(logs, logp);
        TextView footer = label("© 2026 AimAi Max", 11, muted, false); footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(8)); column.addView(footer, lp(-1, -2));
        return scroll;
    }

    private void sectionLabel(LinearLayout parent, String text) {
        TextView t = label(text, 10, muted, false);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(4), dp(20), 0, dp(10));
        parent.addView(t);
    }

    private void addSettingRow(LinearLayout parent, int icon, String name, String value,
                               View trailing, boolean last, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(14), 0);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(box(Color.rgb(24, 25, 38), 8, line));
        IconView iv = new IconView(this, icon);
        iv.setColor(Color.rgb(190, 193, 210));
        badge.addView(iv, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        row.addView(badge, lp(40, 40));
        TextView n = label(name, 15, Color.WHITE, false);
        n.setPadding(dp(14), 0, 0, 0);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) row.addView(label(value, 13, muted, false));
        if (trailing != null) row.addView(trailing);
        if (click != null) row.setOnClickListener(click);
        parent.addView(row, lp(-1, 52));
        if (!last) parent.addView(divider());
    }

    // ------------------------------------------------------------------ dialogs / helpers

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "—";
        }
    }

    private String versionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return String.valueOf(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
        } catch (Exception e) {
            return "—";
        }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("AimAi Max")
                .setMessage("Version " + versionName()
                        + "\nCarrom Disc Pool visual assistant.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLegal() {
        new AlertDialog.Builder(this)
                .setTitle("Legal")
                .setMessage("AimAi Max draws an on-screen guide while Carrom Disc Pool is open. "
                        + "It uses screen capture and the accessibility service only while the "
                        + "assistant is running. You are responsible for following the rules of "
                        + "the game you play.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String readLogs() {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "500"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s;
            while ((s = r.readLine()) != null) out.append(s).append('\n');
            r.close();
        } catch (Exception ignored) {
        }
        return out.toString();
    }

    private void shareLogs() {
        String logs = readLogs();
        if (logs.isEmpty()) {
            Toast.makeText(this, "No logs to share.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, logs);
        startActivity(Intent.createChooser(send, "Share logs"));
    }

    private void clearLogs() {
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
            Toast.makeText(this, "Logs cleared.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not clear logs.", Toast.LENGTH_SHORT).show();
        }
    }
}
