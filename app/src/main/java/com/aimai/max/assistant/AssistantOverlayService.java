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

    private WindowManager windowManager;
    private AimGuideView aimGuideView;
    private LinearLayout panel;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    private boolean enabled = true;
    private String selectedStyle = "FOCUS";
    private int accent = Color.rgb(0, 220, 190);
    private int thickness = 5;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener liveGuidePreferencesListener;

    private boolean boardReceiverRegistered = false;

    private boolean pieceReceiverRegistered = false;

    private final android.content.BroadcastReceiver pieceReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context,
                              android.content.Intent intent) {
            if (aimGuideView == null || !enabled) return;

            if (!intent.getBooleanExtra("isStriker", false)) {
    aimGuideView.setGuideEnabled(false);
    return;
}

            float x = intent.getFloatExtra("pieceX", 0.5f);
            float y = intent.getFloatExtra("pieceY", 0.8f);

            boolean targetFound = intent.getBooleanExtra("targetFound", false);
            if (targetFound) {
                if (intent.getBooleanExtra("shotFound", false)) {
                    aimGuideView.setTrajectory(
                            x, y,
                            intent.getFloatExtra("ghostX", x),
                            intent.getFloatExtra("ghostY", y),
                            intent.getFloatExtra("shotTargetX", x),
                            intent.getFloatExtra("shotTargetY", y),
                            intent.getFloatExtra("pocketX", x),
                            intent.getFloatExtra("pocketY", y));
                    aimGuideView.setGuideEnabled(true);
                    return;
                }
                aimGuideView.clearTrajectory();
                float targetX = intent.getFloatExtra("targetPieceX", x);
                float targetY = intent.getFloatExtra("targetPieceY", y);
                CarromPhysicsEngine.CollisionPrediction prediction =
                        CarromPhysicsEngine.predictCollision(
                                x, y, targetX, targetY);

                if (prediction.valid) {
                    float strikerRadiusX = intent.getFloatExtra("strikerRadiusX", 0f);
                    float strikerRadiusY = intent.getFloatExtra("strikerRadiusY", 0f);
                    float targetRadiusX = intent.getFloatExtra("targetPieceRadiusX", 0f);
                    float targetRadiusY = intent.getFloatExtra("targetPieceRadiusY", 0f);

                    float[] contact = CarromPhysicsEngine.calculateCollisionContactXY(
                            x, y, targetX, targetY,
                            strikerRadiusX, strikerRadiusY,
                            targetRadiusX, targetRadiusY);

                    if (contact != null) {
                        aimGuideView.setGuide(
                                x, y,
                                Math.max(0f, Math.min(1f, contact[0])),
                                Math.max(0f, Math.min(1f, contact[1])));
                    } else {
                        aimGuideView.setGuide(x, y, targetX, targetY);
                    }
                } else {
                    aimGuideView.setGuide(x, y, targetX, targetY);
                }
            } else {
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

            boolean found = intent.getBooleanExtra("found", false);
            if (!found) {
                aimGuideView.setGuideEnabled(false);
                return;
            }

            aimGuideView.setGuideEnabled(true);
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
        aimGuideView.setGuideStyle(selectedStyle);
        aimGuideView.setGuideThickness(thickness);
        liveGuidePreferencesListener = (preferences, key) -> {
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

    }

    private void createPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(shape(Color.rgb(13, 18, 29), 16));

        TextView header = label("✦  AIMAI MAX                         ×", 15);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(accent);
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        header.setOnClickListener(v -> panel.setVisibility(View.GONE));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(body, new LinearLayout.LayoutParams(-1, dp(250)));

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackground(shape(Color.rgb(22, 29, 43), 10));
        body.addView(sidebar, new LinearLayout.LayoutParams(dp(64), -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(9), dp(3), 0, 0);
        body.addView(content, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView section = label("DRAWINGS", 12);
        section.setTextColor(accent);
        content.addView(section);

        String[] categories = {"Drawings", "Automation", "Skins", "System"};
        String[] icons = {"✧", "◉", "◈", "⚙"};

        java.util.function.Consumer<String> showCategory = category -> {
            content.removeAllViews();
            TextView title = label(category.toUpperCase(), 12);
            title.setTextColor(accent);
            content.addView(title);
            if ("Drawings".equals(category)) {
                String[] styles = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};
                for (String style : styles) {
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(5), 0, dp(5), 0);
                    row.setBackground(shape(Color.rgb(27, 35, 49), 8));
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(35));
                    rp.setMargins(0, dp(3), 0, dp(3));
                    content.addView(row, rp);
                    TextView preview = label("━━━━", 12);
                    preview.setTextColor("RAINBOW".equals(style) ? Color.MAGENTA : accent);
                    row.addView(preview, new LinearLayout.LayoutParams(dp(64), -1));
                    TextView name = label(style, 11);
                    row.addView(name, new LinearLayout.LayoutParams(0, -1, 1f));
                    row.setOnClickListener(v -> {
                        selectedStyle = style;
                        aimGuideView.setGuideStyle(style);
                        String[] all = {"FOCUS","GLOW","FLOW","RAINBOW","PULSE","AURORA"};
                        getSharedPreferences("aimai_settings", MODE_PRIVATE).edit()
                                .putInt("guide_style", java.util.Arrays.asList(all).indexOf(style)).apply();
                    });
                }
                TextView thicknessText = label("LINE WIDTH  " + thickness, 11);
                content.addView(thicknessText);
                SeekBar seek = new SeekBar(this);
                seek.setMax(20); seek.setProgress(thickness);
                seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        thickness = Math.max(1, value);
                        aimGuideView.setGuideThickness(thickness);
                        thicknessText.setText("LINE WIDTH  " + thickness);
                        getSharedPreferences("aimai_settings", MODE_PRIVATE).edit()
                                .putInt("guide_thickness", thickness).apply();
                    }
                    public void onStartTrackingTouch(SeekBar bar) {}
                    public void onStopTrackingTouch(SeekBar bar) {}
                });
                content.addView(seek);
            } else if ("Automation".equals(category)) {
                TextView note = label("ASSIST MODE", 13);
                note.setTextColor(accent); content.addView(note);
                TextView description = label("Live aim guidance. You remain in control and confirm every shot.", 11);
                description.setTextColor(Color.LTGRAY); content.addView(description);
                Button scanNow = makeButton("PHOTO / SCAN BOARD NOW");
                scanNow.setOnClickListener(v -> {
                    Intent scan = new Intent(ScreenCaptureService.ACTION_SCAN_NOW);
                    scan.setPackage(getPackageName());
                    sendBroadcast(scan);
                    Toast.makeText(this, "Scanning the current board...", Toast.LENGTH_SHORT).show();
                });
                content.addView(scanNow, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
                Switch assist = new Switch(this);
                assist.setText("Guide enabled"); assist.setTextColor(Color.WHITE);
                assist.setChecked(enabled);
                assist.setOnCheckedChangeListener((button, checked) -> {
                    enabled = checked; aimGuideView.setGuideEnabled(checked);
                });
                content.addView(assist);
            } else if ("Skins".equals(category)) {
                TextView note = label("ACCENT COLOR", 12); note.setTextColor(accent);
                content.addView(note);
                LinearLayout colors = new LinearLayout(this);
                int[] palette = {Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED, Color.YELLOW, Color.WHITE};
                for (int color : palette) {
                    View swatch = new View(this); swatch.setBackground(shape(color, 15));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(28), 1);
                    cp.setMargins(dp(2), dp(6), dp(2), dp(6)); colors.addView(swatch, cp);
                    swatch.setOnClickListener(v -> {
                        accent = color; aimGuideView.setAccentColor(color);
                        bubble.setBackground(shape(color, 25));
                    });
                }
                content.addView(colors);
            } else {
                TextView status = label("SYSTEM STATUS", 12);
                status.setTextColor(accent); content.addView(status);
                content.addView(label("Screen capture and overlay require Android permission.", 11));
                Button close = makeButton("STOP ASSISTANT");
                close.setOnClickListener(v -> stopSelf()); content.addView(close);
            }
        };

        for (int i = 0; i < categories.length; i++) {
            final String category = categories[i];
            TextView tab = label(icons[i], 19);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(shape(Color.rgb(34, 43, 59), 9));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(43));
            tp.setMargins(dp(3), dp(3), dp(3), dp(3));
            sidebar.addView(tab, tp);
            tab.setOnClickListener(v -> showCategory.accept(category));
            tab.setContentDescription(category);
        }
        showCategory.accept("Drawings");

        params = new WindowManager.LayoutParams(dp(292), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(8); params.y = dp(90);

        WindowManager.LayoutParams guideParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowManager.addView(aimGuideView, guideParams);
        windowManager.addView(panel, params);
        panel.setVisibility(View.GONE);

        bubble = label("✦", 22);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackground(shape(accent, 25));
        WindowManager.LayoutParams bubbleParams = new WindowManager.LayoutParams(
                dp(50), dp(50),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        bubbleParams.x = dp(8);
        windowManager.addView(bubble, bubbleParams);
        bubble.setOnClickListener(v -> panel.setVisibility(
                panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
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
