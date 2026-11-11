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
    private static final String[] TITLES = {"Games.", "Pass.", "Sellers.", "Settings."};
    private static final int[] NAV_ICONS = {IconView.HOME, IconView.CROWN, IconView.TROPHY, IconView.GEAR};

    /** Fill in real sellers here as {"@handle", "Region"}. Empty list shows "No sellers listed yet." */
    private static final String[][] SELLERS = {};

    private final int bg = Color.rgb(5, 6, 10);
    private final int card = Color.rgb(15, 17, 24);
    private final int line = Color.rgb(32, 35, 46);
    private final int purple = Color.rgb(124, 111, 184);
    private final int purpleLight = Color.rgb(166, 156, 235);
    private final int navPill = Color.rgb(44, 44, 78);
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
        input.setHint("Password");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enter password")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String key = input.getText().toString().trim();
            if (key.isEmpty()) {
                input.setError("Enter the password");
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
                if (result == LicenseManager.Result.INVALID) input.setError("Wrong password");
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
        header.setPadding(dp(20), dp(14), dp(20), dp(14));

        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(6), dp(5), dp(12), dp(5));
        pill.setBackground(box(card, 8, line));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aimai_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pill.addView(logo, lp(28, 28));
        TextView brand = label("AimAi ", 12, muted, false);
        TextView engine = label("Max", 12, purpleLight, true);
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setPadding(dp(8), 0, 0, 0);
        brandRow.addView(brand);
        brandRow.addView(engine);
        pill.addView(brandRow);
        header.addView(pill, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        title = label("Games.", 22, Color.WHITE, true);
        header.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(roundButton(IconView.CHAT, v -> showPage(2)), lp(32, 32));
        View.OnClickListener about = v -> showAbout();
        LinearLayout.LayoutParams second = lp(32, 32);
        second.leftMargin = dp(8);
        right.addView(roundButton(IconView.INFO, about), second);
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
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
        wrap.setPadding(dp(30), dp(6), dp(30), dp(14));

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView premium = label("Premium  →", 11, Color.rgb(235, 232, 250), true);
        premium.setGravity(Gravity.CENTER);
        premium.setPadding(dp(14), dp(5), dp(14), dp(5));
        AimAiUiStyle.surface(premium, true);
        premium.setOnClickListener(v -> showPage(1));
        premiumPill = premium;
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.bottomMargin = dp(10);
        stack.addView(premium, pp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(box(Color.rgb(13, 14, 21), 12, line));
        for (int i = 0; i < 4; i++) {
            final int index = i;
            FrameLayout cell = new FrameLayout(this);
            View pillBg = new View(this);
            pillBg.setBackground(rounded(navPill, 9));
            pillBg.setVisibility(View.INVISIBLE);
            cell.addView(pillBg, new FrameLayout.LayoutParams(-1, -1));
            IconView icon = new IconView(this, NAV_ICONS[i]);
            cell.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            cell.setOnClickListener(v -> showPage(index));
            navIcons[i] = icon;
            navPills[i] = pillBg;
            nav.addView(cell, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        stack.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        wrap.addView(stack, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void showPage(int index) {
        current = index;
        title.setText(TITLES[index]);
        for (int i = 0; i < 4; i++) {
            boolean on = i == index;
            navPills[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            navIcons[i].setColor(on ? purpleLight : muted);
        }
        premiumPill.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        host.removeAllViews();
        View page;
        switch (index) {
            case 1: page = buildPass(); break;
            case 2: page = buildSellers(); break;
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

        column.addView(new BannerView(this), lp(-1, 150));
        View permissions = buildPermissionCard();
        if (permissions != null) {
            LinearLayout.LayoutParams pcp = lp(-1, -2);
            pcp.topMargin = dp(14);
            column.addView(permissions, pcp);
        }

        LinearLayout gameCard = new LinearLayout(this);
        gameCard.setOrientation(LinearLayout.VERTICAL);
        gameCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        gameCard.setBackground(box(card, 10, line));
        LinearLayout.LayoutParams cp = lp(-1, -2);
        cp.topMargin = dp(18);
        column.addView(gameCard, cp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new BoardIconView(this), lp(62, 62));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);
        text.addView(label("Carrom Disc Pool", 17, Color.WHITE, false));
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, dp(5), 0, 0);
        View dot = new View(this);
        dot.setBackground(rounded(Color.rgb(112, 190, 140), 4));
        status.addView(dot, lp(8, 8));
        TextView ready = label("Ready", 12, muted, false);
        ready.setPadding(dp(8), 0, 0, 0);
        status.addView(ready);
        text.addView(status);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout more = new FrameLayout(this);
        AimAiUiStyle.surface(more, false);
        TextView dots = label("•••", 12, muted, true);
        dots.setGravity(Gravity.CENTER);
        more.addView(dots, new FrameLayout.LayoutParams(-1, -1));
        more.setOnClickListener(v -> showMoreMenu(v));
        row.addView(more, lp(34, 34));
        gameCard.addView(row);

        LinearLayout launch = new LinearLayout(this);
        launch.setGravity(Gravity.CENTER_VERTICAL);
        launch.setPadding(dp(18), 0, dp(18), 0);
        AimAiUiStyle.surface(launch, true);
        TextView launchText = label("Launch", 13, Color.rgb(240, 238, 252), false);
        launch.addView(launchText, new LinearLayout.LayoutParams(0, -2, 1f));
        launch.addView(label("→", 18, Color.rgb(240, 238, 252), false));
        launch.setOnClickListener(v -> launchWithPassword());
        LinearLayout.LayoutParams lpLaunch = lp(-1, 38);
        lpLaunch.topMargin = dp(16);
        gameCard.addView(launch, lpLaunch);
        return scroll;
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

        TextView find = label("Find sellers", 14, Color.WHITE, false);
        find.setGravity(Gravity.CENTER);
        find.setBackground(box(Color.rgb(24, 25, 42), 16, Color.rgb(52, 54, 90)));
        column.addView(find, lp(-1, 48));

        LinearLayout network = new LinearLayout(this);
        network.setOrientation(LinearLayout.VERTICAL);
        network.setPadding(dp(20), dp(18), dp(20), dp(18));
        network.setBackground(box(Color.rgb(24, 25, 42), 24, Color.rgb(52, 54, 90)));
        TextView count = label(SELLERS.length + " sellers", 10, purpleLight, false);
        network.addView(count);
        TextView head = label("Official seller network", 22, Color.WHITE, true);
        head.setPadding(0, dp(6), 0, dp(6));
        network.addView(head);
        network.addView(label("Choose a seller and contact them directly.", 13, muted, false));
        LinearLayout.LayoutParams np = lp(-1, -2);
        np.topMargin = dp(14);
        column.addView(network, np);

        TextView chip = label("All · " + SELLERS.length, 13, Color.WHITE, true);
        chip.setPadding(dp(16), dp(10), dp(16), dp(10));
        chip.setBackground(box(Color.rgb(24, 25, 42), 18, Color.rgb(52, 54, 90)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = dp(16);
        column.addView(chip, cp);

        if (SELLERS.length == 0) {
            TextView empty = label("No sellers listed yet.", 14, muted, false);
            empty.setPadding(dp(4), dp(24), 0, 0);
            column.addView(empty);
        }
        for (String[] seller : SELLERS) {
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(14), dp(14), dp(14));
            item.setBackground(box(card, 22, line));
            TextView avatar = label("@", 18, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(rounded(Color.rgb(44, 66, 58), 26));
            item.addView(avatar, lp(52, 52));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(14), 0, 0, 0);
            info.addView(label(seller[0], 16, Color.WHITE, false));
            info.addView(label(seller.length > 1 ? seller[1] : "Global", 12, muted, false));
            item.addView(info);
            LinearLayout.LayoutParams ip = lp(-1, -2);
            ip.topMargin = dp(12);
            column.addView(item, ip);
        }
        return scroll;
    }

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = pageColumn(scroll);
        SharedPreferences prefs = getSharedPreferences("aimai_settings", MODE_PRIVATE);

        sectionLabel(column, "ASSISTANT");
        LinearLayout assistant = groupCard();
        boolean accessibility = AimAiGestureService.isConnected();
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        addSettingRow(assistant, IconView.ACCESS, "Accessibility (Auto Play)",
                accessibility ? "On" : "Off", null, false,
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addSettingRow(assistant, IconView.LAYERS, "Display over other apps",
                overlay ? "On" : "Off", null, true,
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        column.addView(assistant, lp(-1, -2));

        sectionLabel(column, "DISPLAY");
        LinearLayout display = groupCard();
        Switch awake = new Switch(this);
        awake.setChecked(prefs.getBoolean("keep_screen_awake", false));
        awake.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("keep_screen_awake", checked).apply();
            if (checked) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        addSettingRow(display, IconView.CHIP, "Keep screen awake", null, awake, true, null);
        column.addView(display, lp(-1, -2));

        sectionLabel(column, "APP");
        LinearLayout app = groupCard();
        addSettingRow(app, IconView.DOC, "Version", versionName(), null, false, null);
        addSettingRow(app, IconView.CHIP, "Architecture",
                Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toUpperCase() : "—",
                null, false, null);
        addSettingRow(app, IconView.INFO, "Legal", "›", null, true, v -> showLegal());
        column.addView(app, lp(-1, -2));

        sectionLabel(column, "LOGS");
        LinearLayout logs = groupCard();
        addSettingRow(logs, IconView.SHARE, "Share logs", "›", null, false, v -> shareLogs());
        final TextView[] sizeHolder = new TextView[1];
        addSettingRow(logs, IconView.TRASH, "Clear logs", "", null, true, v -> {
            clearLogs();
            if (sizeHolder[0] != null) sizeHolder[0].setText("cleared");
        });
        column.addView(logs, lp(-1, -2));

        TextView footer = label("© 2026 AimAi Max.", 11, Color.rgb(100, 104, 118), false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, dp(8));
        column.addView(footer, lp(-1, -2));
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
