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
    private WindowManager.LayoutParams params;
    private boolean enabled = true;
    private String selectedStyle = "FOCUS";
    private int accent = Color.rgb(0, 220, 190);
    private int thickness = 5;

    private boolean boardReceiverRegistered = false;

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

            float cx = intent.getFloatExtra("centerX", 0.5f);
            float cy = intent.getFloatExtra("centerY", 0.5f);
            float bh = intent.getFloatExtra("boardHeight", 0.3f);

            float startY = Math.min(0.95f, cy + bh * 0.40f);
            aimGuideView.setGuide(cx, startY, cx, cy);
            aimGuideView.setGuideEnabled(enabled);
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
    }

    private void createPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(shape(Color.rgb(15, 20, 32), 20));

        TextView header = label("✦  AIMAI MAX     ⋮", 17);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(accent);
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45)));

        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = params.x;
                    startY = params.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + (int) (event.getRawX() - touchX);
                    params.y = startY + (int) (event.getRawY() - touchY);
                    windowManager.updateViewLayout(panel, params);
                    return true;
                }
                return true;
            }
        });

        Switch toggle = new Switch(this);
        toggle.setText("Assistant Enabled");
        toggle.setTextColor(Color.WHITE);
        toggle.setChecked(enabled);
        toggle.setPadding(dp(4), 0, dp(4), 0);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            enabled = checked;
            Toast.makeText(this, checked ? "Assistant enabled" : "Assistant disabled",
                    Toast.LENGTH_SHORT).show();
        });
        addRow(panel, toggle);

        TextView styleTitle = label("ASSISTANT STYLE", 12);
        styleTitle.setTextColor(accent);
        panel.addView(styleTitle);

        String[] styles = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};
        for (int i = 0; i < styles.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = i; j < Math.min(i + 2, styles.length); j++) {
                String style = styles[j];
                Button button = makeButton(style);
                LinearLayout.LayoutParams buttonParams =
                        new LinearLayout.LayoutParams(0, dp(43), 1);
                buttonParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(button, buttonParams);

                button.setOnClickListener(v -> {
                    if (!enabled) {
                        Toast.makeText(this, "Enable Assistant first",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedStyle = style;
                aimGuideView.setGuideStyle(selectedStyle);
                    Toast.makeText(this, style + " selected",
                            Toast.LENGTH_SHORT).show();
                });
            }
            panel.addView(row);
        }

        TextView thicknessTitle = label("THICKNESS: " + thickness, 13);
        thicknessTitle.setTextColor(Color.LTGRAY);
        panel.addView(thicknessTitle);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(20);
        seekBar.setProgress(thickness);
        seekBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(
                android.content.res.ColorStateList.valueOf(accent));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                thickness = Math.max(1, value);
                thicknessTitle.setText("THICKNESS: " + thickness);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        panel.addView(seekBar);

        TextView colorTitle = label("ACCENT COLOR", 12);
        colorTitle.setTextColor(Color.LTGRAY);
        panel.addView(colorTitle);

        LinearLayout colors = new LinearLayout(this);
        int[] palette = {
                Color.rgb(0, 220, 190),
                Color.rgb(70, 140, 255),
                Color.rgb(190, 90, 255),
                Color.rgb(255, 90, 130),
                Color.rgb(255, 190, 55),
                Color.WHITE
        };

        for (int color : palette) {
            View swatch = new View(this);
            swatch.setBackground(shape(color, 20));
            LinearLayout.LayoutParams colorParams =
                    new LinearLayout.LayoutParams(0, dp(30), 1);
            colorParams.setMargins(dp(3), dp(7), dp(3), dp(7));
            colors.addView(swatch, colorParams);
            swatch.setOnClickListener(v -> {
                accent = color;
                header.setTextColor(accent);
                seekBar.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(accent));
                seekBar.setThumbTintList(
                        android.content.res.ColorStateList.valueOf(accent));
            });
        }
        panel.addView(colors);

        Button close = makeButton("CLOSE ASSISTANT");
        close.setBackground(shape(Color.rgb(145, 42, 55), 12));
        addRow(panel, close);
        close.setOnClickListener(v -> stopSelf());

        params = new WindowManager.LayoutParams(
                dp(290),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(100);

        windowManager.addView(panel, params);
        WindowManager.LayoutParams guideParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        windowManager.addView(aimGuideView, guideParams);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (boardReceiverRegistered) {
            try {
                unregisterReceiver(boardReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            boardReceiverRegistered = false;
        }

        if (panel != null && windowManager != null) {
            windowManager.removeView(panel);
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
