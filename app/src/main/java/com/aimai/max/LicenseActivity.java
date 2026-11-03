package com.aimai.max;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LicenseActivity extends Activity {
    private EditText input;
    private Button button;
    private TextView status;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int background = Color.rgb(10, 13, 19);
        int accent = Color.rgb(0, 214, 190);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(background);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("AimAi Max");
        title.setTextSize(29);
        title.setTextColor(accent);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter your license key to continue");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(24));
        root.addView(subtitle);

        input = new EditText(this);
        input.setHint("AIK-XXXX-XXXX-XXXX-XXXX");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        button = new Button(this);
        button.setText("Activate");
        button.setAllCaps(false);
        root.addView(button, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.rgb(255, 107, 107));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);

        String message = getIntent().getStringExtra("msg");
        if (message != null) status.setText(message);
        button.setOnClickListener(v -> activate());
        setContentView(root);
    }

    private void activate() {
        String key = input.getText().toString().trim();
        if (key.isEmpty()) {
            status.setTextColor(Color.rgb(255, 107, 107));
            status.setText("Please enter your license key.");
            return;
        }
        button.setEnabled(false);
        status.setTextColor(Color.LTGRAY);
        status.setText("Verifying license...");
        LicenseManager.verify(this, key, result -> {
            if (isFinishing()) return;
            button.setEnabled(true);
            if (result == LicenseManager.Result.OK) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            status.setTextColor(Color.rgb(255, 107, 107));
            if (result == LicenseManager.Result.INVALID) status.setText("This license key is invalid or inactive.");
            else if (result == LicenseManager.Result.OTHER_DEVICE) status.setText("This license key is already registered to another device.");
            else status.setText("Unable to reach the license server. Check your connection and try again.");
        });
    }
}