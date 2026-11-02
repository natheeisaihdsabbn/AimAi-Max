package com.aimai.max.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class FeatureScreens {

    private FeatureScreens() {}

    public static View create(Activity activity, String screen) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.rgb(12, 15, 25));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 32, 24, 32);
        scroll.addView(content);

        addText(activity, content, "AimAi Max", 28, Color.WHITE, true);
        addText(activity, content, screen, 23, Color.rgb(80, 190, 255), true);

        switch (screen) {
            case "Friends":
                addText(activity, content, "Connect and play with friends.", 16, Color.LTGRAY, false);
                android.widget.EditText friendInput = new android.widget.EditText(activity);
                friendInput.setHint("Enter friend name or ID");
                friendInput.setSingleLine(true);
                content.addView(friendInput);
                android.widget.Button invite = new android.widget.Button(activity);
                invite.setText("Send Invite");
                content.addView(invite);
                android.widget.TextView status = new android.widget.TextView(activity);
                status.setTextColor(Color.LTGRAY);
                content.addView(status);
                invite.setOnClickListener(v -> {
                    String value = friendInput.getText().toString().trim();
                    status.setText(value.isEmpty()
                            ? "Enter a friend name or ID."
                            : "Invite prepared for: " + value);
                });
                addText(activity, content, "Friends List", 18, Color.WHITE, true);
                addText(activity, content, "No friends added yet.", 15, Color.LTGRAY, false);
                break;
            case "Equipment":
                addText(activity, content, "Manage your playing equipment.", 16, Color.LTGRAY, false);
                addText(activity, content, "Select Guide Equipment", 18, Color.WHITE, true);
                String[] items = {"Classic Guide", "Precision Guide", "Glow Guide", "Pro Guide"};
                android.widget.Spinner equipment = new android.widget.Spinner(activity);
                android.content.SharedPreferences equipmentPrefs = activity.getSharedPreferences("aimai_settings", 0);
                equipment.setAdapter(new android.widget.ArrayAdapter<>(
                        activity, android.R.layout.simple_spinner_dropdown_item, items));
                content.addView(equipment);
                equipment.setSelection(equipmentPrefs.getInt("equipment_choice", 0));
                equipment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                        equipmentPrefs.edit().putInt("equipment_choice", position).apply();
                    }
                });
                android.widget.TextView selected = new android.widget.TextView(activity);
                selected.setTextColor(Color.LTGRAY);
                content.addView(selected);
                equipment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                            android.view.View view, int position, long id) {
                        selected.setText("Selected: " + items[position]);
                        equipmentPrefs.edit().putInt("equipment_choice", position).apply();
                    }
                });
                break;
            case "Events":
                addText(activity, content, "Explore upcoming events.", 16, Color.LTGRAY, false);
                String[] events = {"Daily Practice", "Friendly Match", "Pro Challenge"};
                android.content.SharedPreferences eventPrefs = activity.getSharedPreferences("aimai_settings", 0);
                for (int i = 0; i < events.length; i++) {
                    final int index = i;
                    String event = events[i];
                    addText(activity, content, event, 18, Color.WHITE, true);
                    android.widget.Button join = new android.widget.Button(activity);
                    join.setText(eventPrefs.getInt("event_choice", -1) == index ? "Joined" : "Join Event");
                    content.addView(join);
                    join.setOnClickListener(v -> {
                        eventPrefs.edit().putInt("event_choice", index).apply();
                        join.setText("Joined");
                        android.widget.Toast.makeText(activity, "Joined: " + event,
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
                break;
            case "Shop":
                addText(activity, content, "Explore available items.", 16, Color.LTGRAY, false);
                String[] products = {"Blue Guide Theme", "Neon Guide Theme", "Pro Guide Theme"};
                android.content.SharedPreferences shopPrefs = activity.getSharedPreferences("aimai_settings", 0);
                for (int i = 0; i < products.length; i++) {
                    final int index = i;
                    String product = products[i];
                    addText(activity, content, product, 18, Color.WHITE, true);
                    android.widget.Button select = new android.widget.Button(activity);
                    select.setText(shopPrefs.getInt("shop_choice", -1) == index ? "Selected" : "Select Item");
                    content.addView(select);
                    select.setOnClickListener(v -> {
                        shopPrefs.edit().putInt("shop_choice", index).apply();
                        select.setText("Selected");
                        android.widget.Toast.makeText(activity, product + " selected",
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
                break;
            case "Settings":
                addText(activity, content, "Assistant Settings", 20, Color.WHITE, true);

                addText(activity, content, "Guide Style", 16, Color.LTGRAY, true);
                String[] styles = {"FOCUS", "GLOW", "FLOW", "RAINBOW", "PULSE", "AURORA"};
                android.widget.Spinner stylePicker = new android.widget.Spinner(activity);
                android.content.SharedPreferences prefs = activity.getSharedPreferences("aimai_settings", 0);
                stylePicker.setAdapter(new android.widget.ArrayAdapter<>(
                        activity, android.R.layout.simple_spinner_dropdown_item, styles));
                content.addView(stylePicker);
                stylePicker.setSelection(Math.max(0, Math.min(5,
                        prefs.getInt("guide_style", 0))));
                stylePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                        prefs.edit().putInt("guide_style", position).apply();
                    }
                });

                addText(activity, content, "Guide Thickness", 16, Color.LTGRAY, true);
                android.widget.SeekBar thickness = new android.widget.SeekBar(activity);
                thickness.setMax(20);
                thickness.setProgress(Math.max(1, Math.min(20,
                        prefs.getInt("guide_thickness", 8))));
                thickness.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                        prefs.edit().putInt("guide_thickness", progress).apply();
                    }
                    public void onStartTrackingTouch(android.widget.SeekBar bar) {}
                    public void onStopTrackingTouch(android.widget.SeekBar bar) {}
                });
                content.addView(thickness);

                addText(activity, content, "Display", 16, Color.LTGRAY, true);
                android.widget.Switch screenSwitch = new android.widget.Switch(activity);
                screenSwitch.setText("Keep screen awake");
                screenSwitch.setTextColor(Color.WHITE);
                screenSwitch.setChecked(prefs.getBoolean("keep_screen_awake", false));
                screenSwitch.setOnCheckedChangeListener((button, checked) -> {
                    prefs.edit().putBoolean("keep_screen_awake", checked).apply();
                    android.provider.Settings.System.putInt(
                            activity.getContentResolver(),
                            android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                            checked ? 30 * 60 * 1000 : 60 * 1000);
                });
                content.addView(screenSwitch);

                android.widget.Button reset = new android.widget.Button(activity);
                reset.setText("Reset Preferences");
                content.addView(reset);
                reset.setOnClickListener(v -> {
                    prefs.edit().clear().apply();
                    android.widget.Toast.makeText(activity,
                            "Preferences reset. Reopen Settings to apply.",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
                break;
            default:
                addText(activity, content, "Feature not found.", 16, Color.LTGRAY, false);
        }

        return scroll;
    }

    private static void addText(Activity activity, LinearLayout parent,
                                String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(0, 14, 0, 14);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(text);
    }
}
