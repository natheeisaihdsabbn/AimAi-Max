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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AssistantOverlayService extends Service {

    private static final long MIN_SHOT_GAP_MS = 2200L;
    private static final long SETTLE_MIN_MS = 2500L;
    /** If the board never reports a change after a shot, allow a retry after this long. */
    private static final long SETTLE_TIMEOUT_MS = 15000L;

    private WindowManager windowManager;
    private AimGuideView aimGuideView;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private boolean enabled = true;
    private String selectedStyle = "FOCUS";
    private int accent = Color.rgb(0, 220, 190);
    private int thickness = 5;
    private boolean autoPlay = false;
    private long lastAutoShotAt = 0L;
    private float lastAutoStrikerX = -1f, lastAutoStrikerY = -1f;
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
            // Everything (guide, scanning results, AUTO PLAY) is dead while the game is not in front.
            if (aimGuideView == null || !enabled || !gameForeground
                    || !AimAiGestureService.isGameForeground()) {
                return;
            }

            boolean boardStable = intent.getBooleanExtra("boardStable", false);
            long now = android.os.SystemClock.elapsedRealtime();

            // Track the shot cycle: shot -> pieces move -> pieces stop -> scan again -> next shot.
            if (waitingForSettle) {
                if (!boardStable) sawMotionAfterShot = true;
                boolean settled = boardStable && now - lastAutoShotAt >= SETTLE_MIN_MS
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
                aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
                aimGuideView.setGuideEnabled(false);
                return;
            }

            float x = intent.getFloatExtra("pieceX", 0.5f);
            float y = intent.getFloatExtra("pieceY", 0.8f);
            boolean targetFound = intent.getBooleanExtra("targetFound", false);
            boolean plannedShot = intent.getBooleanExtra("shotFound", false);

            if (autoPlay && !waitingForSettle && boardStable && targetFound && plannedShot
                    && (lastAutoShotAt == 0L || now - lastAutoShotAt > MIN_SHOT_GAP_MS)
                    && AimAiGestureService.isConnected()) {
                float gx = intent.getFloatExtra("ghostX", x);
                float gy = intent.getFloatExtra("ghostY", y);
                boolean strikerMoved = lastAutoStrikerX < 0f
                        || Math.abs(x - lastAutoStrikerX) + Math.abs(y - lastAutoStrikerY) > 0.012f;
                if (strikerMoved) {
                    // Same real-screen space the capture used, so the swipe starts on the striker.
                    int[] size = ScreenMetrics.realSize(AssistantOverlayService.this);
                    int sw = size[0];
                    int sh = size[1];
                    if (AimAiGestureService.swipeShot(x * sw, y * sh, gx * sw, gy * sh, sw, sh,
                            intent.getFloatExtra("shotPower", 0.5f))) {
                        waitingForSettle = true;
                        sawMotionAfterShot = false;
                        lastAutoShotAt = android.os.SystemClock.elapsedRealtime();
                        lastAutoStrikerX = x;
                        lastAutoStrikerY = y;
                    }
                }
            }

            if (targetFound) {
                float targetX = intent.getFloatExtra("targetPieceX", x);
                float targetY = intent.getFloatExtra("targetPieceY", y);
                float ghostX = intent.getFloatExtra("ghostX", targetX);
                float ghostY = intent.getFloatExtra("ghostY", targetY);
                float pocketX = intent.getFloatExtra("pocketX", targetX);
                float pocketY = intent.getFloatExtra("pocketY", targetY);
                if (plannedShot) {
                    aimGuideView.setGuide(x, y, ghostX, ghostY);
                    aimGuideView.setShotRoute(ghostX, ghostY, pocketX, pocketY, true);
                } else {
                    aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
                    float[] contact = CarromPhysicsEngine.calculateCollisionContactXY(
                            x, y, targetX, targetY,
                            intent.getFloatExtra("strikerRadiusX", 0f),
                            intent.getFloatExtra("strikerRadiusY", 0f),
                            intent.getFloatExtra("targetPieceRadiusX", 0f),
                            intent.getFloatExtra("targetPieceRadiusY", 0f));
                    if (contact != null) aimGuideView.setGuide(x, y, contact[0], contact[1]);
                    else aimGuideView.setGuide(x, y, targetX, targetY);
                }
            } else {
                aimGuideView.setShotRoute(0f, 0f, 0f, 0f, false);
                aimGuideView.setGuide(x, y, x, Math.max(0.05f, y - 0.25f));
            }

            aimGuideView.setGuideEnabled(true);
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

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setBackground(shape(Color.rgb(39, 48, 65), 12));
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
        String[] savedStyles = {
                "FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"
        };
        int savedStyle = prefs.getInt("guide_style", 0);
        if (savedStyle >= 0 && savedStyle < savedStyles.length) {
            selectedStyle = savedStyles[savedStyle];
        }
        thickness = Math.max(1, prefs.getInt("guide_thickness", 8));

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

        if (!AimAiGestureService.isConnected() && !warnedNoAccessibility) {
            warnedNoAccessibility = true;
            Toast.makeText(this,
                    "Enable AimAi Max in Accessibility settings so the assistant can tell when Carrom Disc Pool is open.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private TextView bubble;
    private final android.content.BroadcastReceiver gameStateReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            gameForeground = intent.getBooleanExtra("foreground", false);
            // Fresh start on every transition: leaving pauses everything, returning resumes from a clean state.
            waitingForSettle = false;
            sawMotionAfterShot = false;
            lastAutoShotAt = 0L;
            lastAutoStrikerX = -1f;
            lastAutoStrikerY = -1f;
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

    private void createPanel() {
        aimGuideView.setGuideEnabled(false);

        bubble = label("A", 21);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, Typeface.BOLD);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackground(shape(accent, 28));
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

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(shape(Color.rgb(15, 20, 32), 20));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView header = label("AIMAI MAX", 15);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(accent);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button collapse = makeButton("MINIMIZE");
        collapse.setOnClickListener(v -> showBubble());
        headerRow.addView(collapse);
        panel.addView(headerRow);

        Switch toggle = new Switch(this);
        toggle.setText("Assistant Enabled");
        toggle.setTextColor(Color.WHITE);
        toggle.setChecked(enabled);
        toggle.setPadding(dp(4), 0, dp(4), 0);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            enabled = checked;
            if (!checked && aimGuideView != null) aimGuideView.setGuideEnabled(false);
        });
        addRow(panel, toggle);

        Switch autoPlaySwitch = new Switch(this);
        autoPlaySwitch.setText("AUTO PLAY");
        autoPlaySwitch.setTextColor(Color.WHITE);
        autoPlaySwitch.setChecked(autoPlay);
        autoPlaySwitch.setPadding(dp(4), 0, dp(4), 0);
        autoPlaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !AimAiGestureService.isConnected()) {
                autoPlaySwitch.setChecked(false);
                Toast.makeText(this, "Enable AimAi Max Auto Play in Accessibility settings", Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            autoPlay = checked;
            waitingForSettle = false;
            sawMotionAfterShot = false;
            lastAutoShotAt = 0L;
            lastAutoStrikerX = -1f;
            lastAutoStrikerY = -1f;
        });
        addRow(panel, autoPlaySwitch);

        TextView styleTitle = label("GUIDE STYLE", 12);
        styleTitle.setTextColor(accent);
        panel.addView(styleTitle);
        String[] styles = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};
        for (int i = 0; i < styles.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, styles.length); j++) {
                String style = styles[j];
                Button choice = makeButton(style);
                LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
                choiceParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(choice, choiceParams);
                choice.setOnClickListener(v -> {
                    selectedStyle = style;
                    aimGuideView.setGuideStyle(style);
                    getSharedPreferences("aimai_settings", MODE_PRIVATE).edit()
                            .putInt("guide_style", java.util.Arrays.asList(styles).indexOf(style)).apply();
                });
            }
            panel.addView(row);
        }

        TextView thicknessTitle = label("THICKNESS: " + thickness, 12);
        thicknessTitle.setTextColor(Color.LTGRAY);
        panel.addView(thicknessTitle);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(20);
        seekBar.setProgress(thickness);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                thickness = Math.max(1, value);
                thicknessTitle.setText("THICKNESS: " + thickness);
                getSharedPreferences("aimai_settings", MODE_PRIVATE).edit()
                        .putInt("guide_thickness", thickness).apply();
                aimGuideView.setGuideThickness(thickness);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        panel.addView(seekBar);

        Button close = makeButton("CLOSE ASSISTANT");
        close.setBackground(shape(Color.rgb(145, 42, 55), 12));
        addRow(panel, close);
        close.setOnClickListener(v -> stopSelf());

        params = new WindowManager.LayoutParams(
                dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(100);

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
        windowManager.addView(aimGuideView, guideParams);
        windowManager.addView(panel, params);
        applyGameVisibility();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
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

        if (aimGuideView != null && windowManager != null) {
            try {
                windowManager.removeView(aimGuideView);
            } catch (IllegalArgumentException ignored) {
            }
            aimGuideView = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
