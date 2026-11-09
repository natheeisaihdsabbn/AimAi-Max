package com.aimai.max.assistant;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import com.aimai.max.R;
import com.aimai.max.ui.IconView;
import com.aimai.max.ui.AimAiUiStyle;
import com.aimai.max.ui.StylePreviewView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AssistantOverlayService extends Service {

    private static final long MIN_SHOT_GAP_MS = 1000L;
    private static final long SETTLE_MIN_MS = 1200L;
    /** If the board never reports a change after a shot, allow a retry after this long. */
    private static final long SETTLE_TIMEOUT_MS = 10000L;
    /** Wait this long after sliding the striker before looking at the board again. */
    private static final long SLIDER_GAP_MS = 800L;

    private WindowManager windowManager;
    private AimGuideView aimGuideView;
    private ShotPathView pathView;
    private android.widget.FrameLayout overlayRoot;
    private WindowManager.LayoutParams guideLp;
    private float overlayAlpha = -1f;
    private float slideStartErr = 0f;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private boolean enabled = true;
    private String selectedStyle = "FOCUS";
    private int accent = Color.rgb(88, 166, 255);
    private int thickness = 5;
    private boolean autoPlay = false;
    private long lastAutoShotAt = 0L;
    private float lastAutoStrikerX = -1f, lastAutoStrikerY = -1f;
    private long lastSliderAt = 0L;
    private int sliderTries = 0;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener liveGuidePreferencesListener;

    private boolean boardReceiverRegistered = false;
    private boolean pieceReceiverRegistered = false;
    private boolean gameReceiverRegistered = false;
    private boolean gameForeground = false;
    private boolean waitingForSettle = false;
    /** True once the board was seen moving (unstable) after the last shot. */
    private boolean sawMotionAfterShot = false;
    private boolean warnedNoAccessibility = false;

    private final android.content.BroadcastReceiver pieceReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context,
                              android.content.Intent intent) {
            lastScanAt = android.os.SystemClock.elapsedRealtime();
            // Everything (guide, scanning results, AUTO PLAY) is dead while the game is not in front.
            if (aimGuideView == null || !enabled || !gameForeground
                    || !AimAiGestureService.isGameForeground()) {
                return;
            }

            boolean boardStable = intent.getBooleanExtra("boardStable", false);
            boolean boardSteady = intent.getBooleanExtra("boardSteady", boardStable);
            if (statsView != null) {
                statsView.setText("Striker: " + intent.getIntExtra("strikerCount", 0)
                        + "   Guti: " + intent.getIntExtra("gutiCount", 0)
                        + "   Board: " + (boardStable ? "steady" : "moving"));
            }
            long now = android.os.SystemClock.elapsedRealtime();

            // Track the shot cycle: shot -> pieces move -> pieces stop -> scan again -> next shot.
            if (waitingForSettle) {
                if (!boardStable) sawMotionAfterShot = true;
                boolean settled = boardSteady && now - lastAutoShotAt >= SETTLE_MIN_MS
                        && (sawMotionAfterShot || now - lastAutoShotAt >= SETTLE_TIMEOUT_MS);
                if (settled) {
                    waitingForSettle = false;
                    sawMotionAfterShot = false;
                    lastAutoStrikerX = -1f;
                    lastAutoStrikerY = -1f;
                    android.util.Log.i("AimAiAutoPlay", "Board settled; next shot unlocked");
                }
            }

            if (!intent.getBooleanExtra("isStriker", false)) {
                setOverlayAlpha(0f);
                if (pathView != null) pathView.clear();
                aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
                aimGuideView.setGuideEnabled(false);
                return;
            }

            setOverlayAlpha(0.8f);
            if (pathView != null) pathView.clear();
            float x = intent.getFloatExtra("pieceX", 0.5f);
            float y = intent.getFloatExtra("pieceY", 0.8f);
            aimGuideView.setBodyRadius(intent.getFloatExtra("strikerRadius", 0f));
            aimGuideView.setPocketRadius(intent.getFloatExtra("pocketRadius", 0f));
            float sliderY = intent.getFloatExtra("sliderY", 0f);
            boolean curFound = intent.getBooleanExtra("curFound", false);
            boolean bestFound = intent.getBooleanExtra("bestFound", false);
            float curScore = intent.getFloatExtra("curSimScore", -1000f);
            float bestScore = intent.getFloatExtra("bestSimScore", -1000f);
            float bestX = intent.getFloatExtra("bestStrikerX", x);

            // Slide the striker only when another spot is clearly better, and only twice per shot.
            boolean slideOn = settings().getBoolean("slide_enabled", true) && sliderY > 0f;
            boolean wantSlide = slideOn && bestFound && sliderTries < 3 && Math.abs(bestX - x) > 0.015f
                    && (!curFound || bestScore > curScore + 12f);

            // Draw the shot that will be played: the simulated path of the striker and every coin it moves,
            // including wall bounces. "Visible search" also shows the other shots that were tried, thin.
            String drawPre = (wantSlide || !curFound) ? "best" : "cur";
            if ("best".equals(drawPre) && !bestFound) drawPre = "cur";
            if (intent.getBooleanExtra(drawPre + "Found", false)) {
                aimGuideView.setSearch(intent.getFloatArrayExtra(drawPre + "Paths"),
                        settings().getBoolean("visible_search2", false) ? intent.getFloatArrayExtra("curAlts") : null,
                        intent.getFloatExtra(drawPre + "GhostX", -1f),
                        intent.getFloatExtra(drawPre + "GhostY", -1f));
                aimGuideView.setGuideEnabled(true);
            } else {
                aimGuideView.clearSearch();
                aimGuideView.setGuideEnabled(false);
            }

            if (autoPlay && !waitingForSettle && boardSteady && (curFound || bestFound)
                    && (lastAutoShotAt == 0L || now - lastAutoShotAt > minShotGap())
                    && AimAiGestureService.isConnected()) {
                int[] size = ScreenMetrics.realSize(AssistantOverlayService.this);
                int sw = size[0];
                int sh = size[1];
                if (wantSlide) {
                    if (now - lastSliderAt > SLIDER_GAP_MS) {
                        AimAiGestureService.dragSlider(x * sw, bestX * sw, sliderY * sh, sw, sh);
                        sliderTries++;
                        lastSliderAt = now;
                    }
                } else if (curFound || bestFound) {
                    float aimX = curFound ? intent.getFloatExtra("curAimX", x) : intent.getFloatExtra("bestGhostX", x);
                    float aimY = curFound ? intent.getFloatExtra("curAimY", y) : intent.getFloatExtra("bestGhostY", y);
                    boolean pullBack = "PULL".equals(settings().getString("swipe_mode", "FORWARD"));
                    float powerMul = settings().getInt("power_pct", 100) / 100f;
                    AimAiGestureService.swipeDurationMs = humanStyle()
                            ? 480 + (int) (Math.random() * 220) : 260;
                    if (AimAiGestureService.swipeShot(x * sw, y * sh, aimX * sw, aimY * sh, sw, sh,
                            intent.getFloatExtra(curFound ? "curPower" : "bestPower", 0.8f), pullBack, powerMul)) {
                        waitingForSettle = true;
                        sawMotionAfterShot = false;
                        lastAutoShotAt = android.os.SystemClock.elapsedRealtime();
                        lastAutoStrikerX = x;
                        lastAutoStrikerY = y;
                        sliderTries = 0;
                    }
                }
            }
        }
    };

    private final android.content.BroadcastReceiver boardReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context,
                              android.content.Intent intent) {
            if (aimGuideView == null) return;
            // This receiver may only HIDE the guide. Showing it is the piece receiver's job,
            // otherwise a frame without a striker would bring back a stale guide line.
            boolean found = intent.getBooleanExtra("found", false);
            if (!found || !gameForeground || !enabled) {
                aimGuideView.setGuideEnabled(false);
            }
        }
    };

    private long lastHintAt = 0L;

    /** Short on-screen message about what Auto Play is doing, at most one every 1.2 seconds. */
    private void hint(String text) {
        long t = android.os.SystemClock.elapsedRealtime();
        if (t - lastHintAt < 1200L) return;
        lastHintAt = t;
        uiHandler.post(() -> Toast.makeText(AssistantOverlayService.this, text, Toast.LENGTH_SHORT).show());
    }

    /**
     * Android 12 and newer drops touches that pass through an overlay window that is more than 80 percent
     * opaque, so the game would not react to taps. The full-screen drawing window therefore stays
     * invisible (0) while no board is on screen and at most 0.8 while it draws.
     */
    private boolean overlayAttached = false;

    /**
     * The full-screen drawing window only exists while a board is on screen. In the lobby and menus it is
     * removed completely, so it can never get between your finger and the game.
     */
    private void setOverlayAlpha(float a) {
        if (overlayRoot == null || guideLp == null || windowManager == null) return;
        boolean on = a > 0f;
        if (on == overlayAttached) return;
        guideLp.alpha = 0.8f;
        try {
            if (on) {
                windowManager.addView(overlayRoot, guideLp);
            } else {
                windowManager.removeView(overlayRoot);
            }
            overlayAttached = on;
        } catch (Exception ignored) {
        }
    }

    private boolean humanStyle() {
        return "HUMAN".equals(settings().getString("play_style", "FAST"));
    }

    /** Human style waits longer between shots, like a person would. */
    private long minShotGap() {
        return humanStyle() ? 4200L : MIN_SHOT_GAP_MS;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = label(value, size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, Typeface.BOLD);
        return text;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        AimAiUiStyle.button(button);
        return button;
    }

    private void addRow(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.content.SharedPreferences prefs =
                getSharedPreferences("aimai_settings", MODE_PRIVATE);
        // A new session always starts with striker sliding allowed.
        prefs.edit().putBoolean("slide_enabled", true).apply();
        String[] savedStyles = {
                "FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"
        };
        int savedStyle = prefs.getInt("guide_style", 0);
        if (savedStyle >= 0 && savedStyle < savedStyles.length) {
            selectedStyle = savedStyles[savedStyle];
        }
        thickness = Math.max(1, prefs.getInt("guide_thickness", 8));
        accent = prefs.getInt("accent_color", accent);
        smooth = prefs.getBoolean("guide_smooth", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "aimai_assistant", "AimAi Max Assistant",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager =
                getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, "aimai_assistant")
                .setContentTitle("AimAi Max is active")
                .setContentText("Floating Assistant is running")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();

            startForeground(1001, notification);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        aimGuideView = new AimGuideView(this);
        int[] size = ScreenMetrics.realSize(this);
        aimGuideView.setScreenSize(size[0], size[1]);
        aimGuideView.setGuideStyle(selectedStyle);
        aimGuideView.setGuideThickness(thickness);
        aimGuideView.setAccentColor(accent);
        aimGuideView.setSmooth(smooth);
        liveGuidePreferencesListener = (preferences, key) -> {
            if (aimGuideView == null) return;
            if ("guide_style".equals(key)) {
                String[] styles = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};
                int index = preferences.getInt(key, 0);
                if (index >= 0 && index < styles.length) {
                    selectedStyle = styles[index];
                    aimGuideView.setGuideStyle(selectedStyle);
                }
            } else if ("guide_thickness".equals(key)) {
                thickness = Math.max(1, preferences.getInt(key, 8));
                aimGuideView.setGuideThickness(thickness);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(liveGuidePreferencesListener);
        createPanel();

        android.content.IntentFilter boardFilter =
                new android.content.IntentFilter("com.aimai.max.BOARD_ANALYSIS");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(boardReceiver, boardFilter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(boardReceiver, boardFilter);
        }
        boardReceiverRegistered = true;

        android.content.IntentFilter pieceFilter =
                new android.content.IntentFilter("com.aimai.max.GUTI_GUIDE_DATA");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pieceReceiver, pieceFilter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pieceReceiver, pieceFilter);
        }
        pieceReceiverRegistered = true;

        android.content.IntentFilter gameFilter = new android.content.IntentFilter("com.aimai.max.GAME_FOREGROUND");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(gameStateReceiver, gameFilter, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(gameStateReceiver, gameFilter);
        gameReceiverRegistered = true;
        gameForeground = AimAiGestureService.isGameForeground();
        applyGameVisibility();
        lastScanAt = android.os.SystemClock.elapsedRealtime();
        uiHandler.postDelayed(watchdog, 2000L);

        if (!AimAiGestureService.isConnected() && !warnedNoAccessibility) {
            warnedNoAccessibility = true;
            Toast.makeText(this,
                    "Enable AimAi Max in Accessibility settings so the assistant can tell when Carrom Disc Pool is open.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private ImageView bubble;
    private final android.content.BroadcastReceiver gameStateReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            gameForeground = intent.getBooleanExtra("foreground", false);
            // Fresh start on every transition: leaving pauses everything, returning resumes from a clean state.
            waitingForSettle = false;
            sawMotionAfterShot = false;
            lastAutoShotAt = 0L;
            lastAutoStrikerX = -1f;
            lastAutoStrikerY = -1f;
            sliderTries = 0;
            lastSliderAt = 0L;
            applyGameVisibility();
            android.util.Log.i("AimAiAutoPlay", "Overlay foreground gate=" + gameForeground);
        }
    };

    private void applyGameVisibility() {
        if (!gameForeground) {
            if (bubble != null) bubble.setVisibility(View.GONE);
            if (panel != null) panel.setVisibility(View.GONE);
        } else {
            // Back in the game: show the compact bubble again.
            if (panel != null) panel.setVisibility(View.GONE);
            if (bubble != null) bubble.setVisibility(View.VISIBLE);
        }
        if (aimGuideView != null) {
            aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
            aimGuideView.setGuideEnabled(false);
            aimGuideView.setVisibility(gameForeground ? View.VISIBLE : View.GONE);
        }
        if (pathView != null) {
            pathView.clear();
            pathView.setVisibility(gameForeground ? View.VISIBLE : View.GONE);
        }
        setOverlayAlpha(0f);
    }

    private void showPanel() {
        if (!gameForeground) return;
        if (bubble != null) bubble.setVisibility(View.GONE);
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void showBubble() {
        if (!gameForeground) return;
        if (panel != null) panel.setVisibility(View.GONE);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static final int[] ACCENTS = {
            Color.rgb(88, 166, 255), Color.rgb(41, 182, 246), Color.rgb(29, 233, 182),
            Color.rgb(126, 123, 201), Color.rgb(255, 105, 180), Color.rgb(41, 121, 255)};
    private static final String[] STYLE_NAMES = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};

    private boolean smooth = false;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastScanAt = 0L;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (bubble != null) {
                boolean stale = gameForeground
                        && android.os.SystemClock.elapsedRealtime() - lastScanAt > 3500L;
                bubble.setAlpha(stale ? 0.45f : 1f);
                if (stale && aimGuideView != null) {
                    aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
                    aimGuideView.setGuideEnabled(false);
                }
                if (stale && statsView != null) {
                    statsView.setText(AimAiGestureService.lastScanError != 0
                            ? "Screenshot error " + AimAiGestureService.lastScanError
                            + ". In Accessibility settings turn AimAi Max off, then on again."
                            : "No scan data. Open AimAi Max and tap Launch.");
                }
            }
            uiHandler.postDelayed(this, 2000L);
        }
    };
    private TextView statsView;
    private Switch visibleSearchSwitch;
    private android.widget.ScrollView tabScroll;
    private IconView[] tabIcons;
    private StylePreviewView[] styleCards;
    private StylePreviewView[] collisionCards;
    private int currentTab = 0;

    private android.content.SharedPreferences settings() {
        return getSharedPreferences("aimai_settings", MODE_PRIVATE);
    }

    private TextView sectionTitle(String text) {
        TextView t = label(text, 10, Color.rgb(135, 140, 155), false);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(2), dp(14), 0, dp(6));
        return t;
    }

    private Switch accentSwitch(boolean checked) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        AimAiUiStyle.toggle(s);
        return s;
    }

    private LinearLayout switchRow(String text, Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(text, 14, Color.WHITE, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sw);
        return row;
    }

    private void applyAccent() {
        if (bubble != null) bubble.setBackground(shape(accent, 28));
        if (aimGuideView != null) aimGuideView.setAccentColor(accent);
        if (styleCards != null) for (StylePreviewView v : styleCards) v.setAccent(accent);
        if (collisionCards != null) for (StylePreviewView v : collisionCards) v.setAccent(accent);
        refreshRail();
    }

    private void refreshRail() {
        if (tabIcons == null) return;
        for (int i = 0; i < tabIcons.length; i++) {
            tabIcons[i].setColor(i == currentTab ? accent : Color.rgb(120, 126, 142));
        }
    }

    private void showTab(int index) {
        currentTab = index;
        refreshRail();
        if (tabScroll == null) return;
        tabScroll.removeAllViews();
        View content = index == 0 ? buildDrawingsTab() : index == 1 ? buildAutoTab() : buildSystemTab();
        tabScroll.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2));
    }

    private View buildDrawingsTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, dp(6), dp(6));

        Switch enabledSwitch = accentSwitch(enabled);
        enabledSwitch.setOnCheckedChangeListener((b, checked) -> {
            enabled = checked;
            if (!checked && aimGuideView != null) aimGuideView.setGuideEnabled(false);
        });
        col.addView(switchRow("Enabled", enabledSwitch));

        col.addView(sectionTitle("STYLE"));
        styleCards = new StylePreviewView[STYLE_NAMES.length];
        for (int i = 0; i < STYLE_NAMES.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < i + 2; j++) {
                final int index = j;
                String name = STYLE_NAMES[j];
                String shown = name.charAt(0) + name.substring(1).toLowerCase();
                StylePreviewView card = new StylePreviewView(this, name, shown, accent);
                card.setSelectedState(name.equals(selectedStyle));
                card.setOnClickListener(v -> {
                    selectedStyle = STYLE_NAMES[index];
                    aimGuideView.setGuideStyle(selectedStyle);
                    settings().edit().putInt("guide_style", index).apply();
                    for (int k = 0; k < styleCards.length; k++) {
                        styleCards[k].setSelectedState(k == index);
                    }
                });
                styleCards[j] = card;
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(66), 1f);
                cp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(card, cp);
            }
            col.addView(row);
        }

        LinearLayout thickRow = new LinearLayout(this);
        thickRow.setGravity(Gravity.CENTER_VERTICAL);
        thickRow.setPadding(0, dp(14), 0, 0);
        thickRow.addView(label("Thickness", 14, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView thickValue = label(thickness + ".0", 12, Color.rgb(135, 140, 155), false);
        thickRow.addView(thickValue);
        col.addView(thickRow);
        SeekBar seek = new SeekBar(this);
        seek.setMax(20);
        seek.setProgress(thickness);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(126, 123, 201)));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(170, 166, 235)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                thickness = Math.max(1, value);
                thickValue.setText(thickness + ".0");
                settings().edit().putInt("guide_thickness", thickness).apply();
                aimGuideView.setGuideThickness(thickness);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        col.addView(seek);

        col.addView(sectionTitle("COLLISION"));
        collisionCards = new StylePreviewView[2];
        LinearLayout collisionRow = new LinearLayout(this);
        String[] modes = {"SHARP", "SMOOTH"};
        String[] shownModes = {"Sharp", "Smooth"};
        for (int i = 0; i < 2; i++) {
            final int index = i;
            StylePreviewView card = new StylePreviewView(this, modes[i], shownModes[i], accent);
            card.setSelectedState((i == 1) == smooth);
            card.setOnClickListener(v -> {
                smooth = index == 1;
                settings().edit().putBoolean("guide_smooth", smooth).apply();
                aimGuideView.setSmooth(smooth);
                collisionCards[0].setSelectedState(!smooth);
                collisionCards[1].setSelectedState(smooth);
            });
            collisionCards[i] = card;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(58), 1f);
            cp.setMargins(dp(3), dp(3), dp(3), dp(3));
            collisionRow.addView(card, cp);
        }
        col.addView(collisionRow);

        col.addView(sectionTitle("ACCENT"));
        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setGravity(Gravity.CENTER_VERTICAL);
        final View[] dots = new View[ACCENTS.length];
        for (int i = 0; i < ACCENTS.length; i++) {
            final int color = ACCENTS[i];
            View dot = new View(this);
            dots[i] = dot;
            dot.setOnClickListener(v -> {
                accent = color;
                settings().edit().putInt("accent_color", accent).apply();
                for (int k = 0; k < dots.length; k++) {
                    GradientDrawable gd = shape(ACCENTS[k], 15);
                    if (ACCENTS[k] == accent) gd.setStroke(dp(2), Color.WHITE);
                    dots[k].setBackground(gd);
                }
                applyAccent();
            });
            GradientDrawable g = shape(color, 15);
            if (color == accent) g.setStroke(dp(2), Color.WHITE);
            dot.setBackground(g);
            LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(30), dp(30), 1f);
            dp1.setMargins(dp(6), dp(2), dp(6), dp(2));
            dotRow.addView(dot, dp1);
        }
        col.addView(dotRow);
        return col;
    }

    private View buildAutoTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, dp(6), dp(6));
        TextView head = label("Automation", 20, Color.WHITE, true);
        head.setPadding(0, 0, 0, dp(10));
        col.addView(head);

        Switch autoSwitch = accentSwitch(autoPlay);
        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked && !AimAiGestureService.isConnected()) {
                autoSwitch.setChecked(false);
                Toast.makeText(this, "Turn on AimAi Max in Accessibility settings first.",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            autoPlay = checked;
            if (visibleSearchSwitch != null) visibleSearchSwitch.setEnabled(checked);
            waitingForSettle = false;
            sawMotionAfterShot = false;
            lastAutoShotAt = 0L;
            lastAutoStrikerX = -1f;
            lastAutoStrikerY = -1f;
            sliderTries = 0;
            lastSliderAt = 0L;
            settings().edit().putBoolean("slide_enabled", true).apply();
        });
        col.addView(switchRow("Auto Play", autoSwitch));

        Switch searchSwitch = accentSwitch(settings().getBoolean("visible_search2", false));
        searchSwitch.setOnCheckedChangeListener((b, checked) ->
                settings().edit().putBoolean("visible_search2", checked).apply());
        visibleSearchSwitch = searchSwitch;
        searchSwitch.setEnabled(autoPlay);
        col.addView(switchRow("Visible Search", searchSwitch));

        col.addView(sectionTitle("PLAY STYLE"));
        LinearLayout styleRow = new LinearLayout(this);
        final String[] playKeys = {"FAST", "HUMAN"};
        final PlayStyleCardView[] playCards = new PlayStyleCardView[2];
        String savedPlay = settings().getString("play_style", "FAST");
        for (int i = 0; i < 2; i++) {
            final int index = i;
            PlayStyleCardView card = new PlayStyleCardView(this, i == 0 ? "Fast" : "Human", i == 0, accent);
            card.setSelectedState(playKeys[i].equals(savedPlay));
            card.setOnClickListener(v -> {
                settings().edit().putString("play_style", playKeys[index]).apply();
                for (int k = 0; k < 2; k++) {
                    playCards[k].setSelectedState(k == index);
                }
            });
            playCards[i] = card;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(66), 1f);
            cp.setMargins(dp(3), dp(3), dp(3), dp(3));
            styleRow.addView(card, cp);
        }
        col.addView(styleRow);

        col.addView(sectionTitle("QUEUE"));
        Switch queueSwitch = accentSwitch(settings().getBoolean("auto_queue", false));
        queueSwitch.setOnCheckedChangeListener((b, checked) ->
                settings().edit().putBoolean("auto_queue", checked).apply());
        col.addView(switchRow("Auto Queue", queueSwitch));

        final String[] stakeLabels = {"200", "500", "2.6k", "4k 2v2", "5.5k", "11k",
                "27k 2v2", "55k", "140k", "170k 2v2", "280k", "580k"};
        final TextView queueStakeValue = label(settings().getString("queue_stake", "500"), 11,
                Color.rgb(135, 140, 155), false);
        LinearLayout queueRow = new LinearLayout(this);
        queueRow.setGravity(Gravity.CENTER_VERTICAL);
        queueRow.setPadding(dp(2), dp(6), 0, dp(2));
        queueRow.addView(label("\u2022  Queue", 11, Color.rgb(135, 140, 155), false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        queueRow.addView(queueStakeValue);
        col.addView(queueRow);

        col.addView(sectionTitle("GAME"));
        LinearLayout gameRow = new LinearLayout(this);
        final String[] gameKeys = {"DISC", "CARROM", "FREESTYLE"};
        final String[] gameNames = {"Disc Pool", "Carrom", "Freestyle"};
        final TextView[] gameButtons = new TextView[3];
        String savedGame = settings().getString("queue_game", "DISC");
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView gb = label(gameNames[i], 12, Color.WHITE, false);
            gb.setGravity(Gravity.CENTER);
            AimAiUiStyle.chip(gb, gameKeys[i].equals(savedGame));
            gb.setOnClickListener(v -> {
                settings().edit().putString("queue_game", gameKeys[index]).apply();
                for (int k = 0; k < 3; k++) {
                    AimAiUiStyle.chip(gameButtons[k], k == index);
                }
            });
            gameButtons[i] = gb;
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            gp.setMargins(dp(2), dp(3), dp(2), dp(3));
            gameRow.addView(gb, gp);
        }
        col.addView(gameRow);

        final TextView stakeHeaderValue = label(settings().getString("queue_stake", "500"), 12,
                Color.rgb(170, 166, 235), false);
        final TextView stakeChevron = label("\u25BC", 10, Color.rgb(135, 140, 155), false);
        stakeChevron.setPadding(dp(8), 0, 0, 0);
        LinearLayout stakeHeader = new LinearLayout(this);
        stakeHeader.setGravity(Gravity.CENTER_VERTICAL);
        stakeHeader.setPadding(dp(2), dp(14), 0, dp(6));
        stakeHeader.addView(label("Max Stake", 14, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        stakeHeader.addView(stakeHeaderValue);
        stakeHeader.addView(stakeChevron);
        col.addView(stakeHeader);

        final LinearLayout stakeGrid = new LinearLayout(this);
        stakeGrid.setOrientation(LinearLayout.VERTICAL);
        stakeGrid.setVisibility(View.GONE);
        final TextView[] stakeButtons = new TextView[stakeLabels.length];
        String savedStake = settings().getString("queue_stake", "500");
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int cell = 0; cell < 3; cell++) {
                final int index = row * 3 + cell;
                TextView sb = label(stakeLabels[index], 12, Color.rgb(190, 194, 208), false);
                sb.setGravity(Gravity.CENTER);
                sb.setBackground(shape(stakeLabels[index].equals(savedStake)
                        ? Color.rgb(36, 40, 56) : Color.TRANSPARENT, 12));
                sb.setOnClickListener(v -> {
                    settings().edit().putString("queue_stake", stakeLabels[index]).apply();
                    queueStakeValue.setText(stakeLabels[index]);
                    stakeHeaderValue.setText(stakeLabels[index]);
                    for (int k = 0; k < stakeButtons.length; k++) {
                        stakeButtons[k].setBackground(shape(k == index ? Color.rgb(36, 40, 56) : Color.TRANSPARENT, 12));
                    }
                });
                stakeButtons[index] = sb;
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                sp.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(sb, sp);
            }
            stakeGrid.addView(line);
        }
        col.addView(stakeGrid);
        stakeHeader.setOnClickListener(v -> {
            boolean open = stakeGrid.getVisibility() == View.VISIBLE;
            stakeGrid.setVisibility(open ? View.GONE : View.VISIBLE);
            stakeChevron.setText(open ? "\u25BC" : "\u25B2");
        });
        return col;
    }

    private View buildSystemTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, dp(6), dp(6));
        TextView head = label("System", 20, Color.WHITE, true);
        head.setPadding(0, 0, 0, dp(10));
        col.addView(head);

        Switch statsSwitch = accentSwitch(settings().getBoolean("scan_stats", true));
        col.addView(switchRow("Stats", statsSwitch));
        statsView = label("Waiting for a scan…", 12, Color.rgb(170, 174, 190), false);
        statsView.setPadding(0, dp(6), 0, 0);
        statsView.setVisibility(statsSwitch.isChecked() ? View.VISIBLE : View.GONE);
        col.addView(statsView);
        statsSwitch.setOnCheckedChangeListener((b, checked) -> {
            settings().edit().putBoolean("scan_stats", checked).apply();
            statsView.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        col.addView(sectionTitle("SWIPE DIRECTION"));
        LinearLayout swipeRow = new LinearLayout(this);
        final String[] swipeKeys = {"FORWARD", "PULL"};
        final String[] swipeNames = {"Drag forward", "Pull back"};
        final TextView[] swipeButtons = new TextView[2];
        String savedSwipe = settings().getString("swipe_mode", "FORWARD");
        for (int i = 0; i < 2; i++) {
            final int index = i;
            TextView sw = label(swipeNames[i], 13, Color.WHITE, false);
            sw.setGravity(Gravity.CENTER);
            AimAiUiStyle.chip(sw, swipeKeys[i].equals(savedSwipe));
            sw.setOnClickListener(v -> {
                settings().edit().putString("swipe_mode", swipeKeys[index]).apply();
                for (int k = 0; k < 2; k++) {
                    AimAiUiStyle.chip(swipeButtons[k], k == index);
                }
            });
            swipeButtons[i] = sw;
            LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            swp.setMargins(dp(3), dp(3), dp(3), dp(3));
            swipeRow.addView(sw, swp);
        }
        col.addView(swipeRow);

        col.addView(sectionTitle("POWER"));
        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView powerValue = label(settings().getInt("power_pct", 100) + "%", 16, Color.WHITE, true);
        powerValue.setGravity(Gravity.CENTER);
        TextView minus = label("\u2212", 22, Color.WHITE, true);
        minus.setGravity(Gravity.CENTER);
        AimAiUiStyle.chip(minus, false);
        TextView plus = label("+", 22, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        AimAiUiStyle.chip(plus, false);
        minus.setOnClickListener(v -> {
            int value = Math.max(30, settings().getInt("power_pct", 100) - 10);
            settings().edit().putInt("power_pct", value).apply();
            powerValue.setText(value + "%");
        });
        plus.setOnClickListener(v -> {
            int value = Math.min(300, settings().getInt("power_pct", 100) + 10);
            settings().edit().putInt("power_pct", value).apply();
            powerValue.setText(value + "%");
        });
        powerRow.addView(minus, new LinearLayout.LayoutParams(dp(56), dp(44)));
        powerRow.addView(powerValue, new LinearLayout.LayoutParams(0, dp(44), 1f));
        powerRow.addView(plus, new LinearLayout.LayoutParams(dp(56), dp(44)));
        col.addView(powerRow);

        col.addView(sectionTitle("MY COINS"));
        LinearLayout coinRow = new LinearLayout(this);
        final String[] coinKeys = {"AUTO", "BLACK", "WHITE", "ANY"};
        final String[] coinNames = {"Auto", "Black", "White", "Any"};
        final TextView[] coinButtons = new TextView[4];
        String savedCoins = settings().getString("my_coins", "AUTO");
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView b = label(coinNames[i], 12, Color.WHITE, false);
            b.setGravity(Gravity.CENTER);
            AimAiUiStyle.chip(b, coinKeys[i].equals(savedCoins));
            b.setOnClickListener(v -> {
                settings().edit().putString("my_coins", coinKeys[index]).apply();
                for (int k = 0; k < 4; k++) {
                    AimAiUiStyle.chip(coinButtons[k], k == index);
                }
            });
            coinButtons[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            bp.setMargins(dp(2), dp(3), dp(2), dp(3));
            coinRow.addView(b, bp);
        }
        col.addView(coinRow);

        col.addView(sectionTitle("GUIDE"));
        TextView reset = label("Reset guide settings", 13, Color.rgb(224, 128, 96), false);
        reset.setPadding(0, dp(4), 0, dp(10));
        reset.setClickable(true);
        reset.setOnClickListener(v -> {
            selectedStyle = STYLE_NAMES[0];
            thickness = 8;
            smooth = false;
            accent = ACCENTS[0];
            settings().edit().putInt("guide_style", 0).putInt("guide_thickness", 8)
                    .putBoolean("guide_smooth", false).putInt("accent_color", accent).apply();
            aimGuideView.setGuideStyle(selectedStyle);
            aimGuideView.setGuideThickness(thickness);
            aimGuideView.setSmooth(false);
            applyAccent();
            Toast.makeText(this, "Guide settings reset.", Toast.LENGTH_SHORT).show();
        });
        col.addView(reset);

        Button close = makeButton("Close assistant");
        close.setBackground(shape(Color.rgb(120, 40, 52), 12));
        close.setOnClickListener(v -> stopSelf());
        col.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        return col;
    }

    private void createPanel() {
        aimGuideView.setGuideEnabled(false);

        // The floating button shows the app logo, clipped to a circle with a thin accent ring.
        bubble = new ImageView(this);
        bubble.setImageResource(R.drawable.aimai_logo);
        bubble.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bubble.setPadding(dp(2), dp(2), dp(2), dp(2));
        bubble.setBackground(shape(accent, 28));
        bubble.setClipToOutline(true);
        WindowManager.LayoutParams bubbleParams = new WindowManager.LayoutParams(
                dp(54), dp(54),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.END;
        bubbleParams.x = dp(12);
        bubbleParams.y = dp(160);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = bubbleParams.x;
                    startY = bubbleParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int dx = (int) (event.getRawX() - touchX);
                    int dy = (int) (event.getRawY() - touchY);
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true;
                    bubbleParams.x = Math.max(0, startX - dx);
                    bubbleParams.y = Math.max(0, startY + dy);
                    if (bubble != null) windowManager.updateViewLayout(bubble, bubbleParams);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved) showPanel();
                    return true;
                }
                return true;
            }
        });
        windowManager.addView(bubble, bubbleParams);

        // ---- video-style panel: header, icon rail on the left, content on the right
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(12), dp(14));
        GradientDrawable panelBg = shape(Color.argb(245, 12, 14, 20), 20);
        panelBg.setStroke(dp(1), Color.rgb(34, 38, 50));
        panel.setBackground(panelBg);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aimai_logo);
        headerRow.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView brand = label("AIM", 15, Color.WHITE, true);
        brand.setPadding(dp(8), 0, 0, 0);
        headerRow.addView(brand);
        headerRow.addView(label("AI", 15, Color.rgb(135, 140, 155), true));
        View spacer = new View(this);
        headerRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView minimize = label("—", 18, Color.rgb(170, 174, 190), false);
        minimize.setGravity(Gravity.CENTER);
        minimize.setOnClickListener(v -> showBubble());
        headerRow.addView(minimize, new LinearLayout.LayoutParams(dp(40), dp(36)));
        panel.addView(headerRow);

        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, dp(8), 0, 0);
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        int[] railIcons = {IconView.CROSSHAIR, IconView.PLAY, IconView.SLIDERS};
        tabIcons = new IconView[railIcons.length];
        for (int i = 0; i < railIcons.length; i++) {
            final int index = i;
            IconView icon = new IconView(this, railIcons[i]);
            icon.setOnClickListener(v -> showTab(index));
            tabIcons[i] = icon;
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(26), dp(26));
            ip.setMargins(dp(6), dp(10), dp(6), dp(14));
            rail.addView(icon, ip);
        }
        body.addView(rail, new LinearLayout.LayoutParams(dp(40), -2));

        tabScroll = new android.widget.ScrollView(this);
        tabScroll.setVerticalScrollBarEnabled(false);
        body.addView(tabScroll, new LinearLayout.LayoutParams(0, dp(330), 1f));
        panel.addView(body);
        showTab(0);

        int[] size = ScreenMetrics.realSize(this);
        int panelWidth = Math.min(size[0] - dp(24), dp(390));
        params = new WindowManager.LayoutParams(
                panelWidth, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(110);

        WindowManager.LayoutParams guideParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            guideParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // One full-screen window holds both drawings, so only one overlay can block touches (see setOverlayAlpha).
        pathView = new ShotPathView(this);
        pathView.setScreenSize(size[0], size[1]);
        overlayRoot = new android.widget.FrameLayout(this);
        overlayRoot.addView(aimGuideView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        overlayRoot.addView(pathView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        guideParams.alpha = 0.8f;
        guideLp = guideParams;
        windowManager.addView(panel, params);
        applyGameVisibility();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        uiHandler.removeCallbacks(watchdog);
        AimAiGestureService.setScanEnabled(false);
        if (liveGuidePreferencesListener != null) {
            getSharedPreferences("aimai_settings", MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(liveGuidePreferencesListener);
            liveGuidePreferencesListener = null;
        }

        if (boardReceiverRegistered) {
            try {
                unregisterReceiver(boardReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            boardReceiverRegistered = false;
        }

        if (pieceReceiverRegistered) {
            try {
                unregisterReceiver(pieceReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            pieceReceiverRegistered = false;
        }

        if (gameReceiverRegistered) {
            try { unregisterReceiver(gameStateReceiver); } catch (IllegalArgumentException ignored) {}
            gameReceiverRegistered = false;
        }
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (IllegalArgumentException ignored) {}
            bubble = null;
        }

        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (IllegalArgumentException ignored) {}
            panel = null;
        }

        pathView = null;

        if (aimGuideView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (IllegalArgumentException ignored) {
            }
            overlayRoot = null;
            aimGuideView = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
