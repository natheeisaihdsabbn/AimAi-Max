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
    private Button btn;
    private TextView status;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = Color.parseColor("#0B0F14");
        int gold = Color.parseColor("#D4AF37");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(bg);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("AimAi Max");
        title.setTextSize(28);
        title.setTextColor(gold);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("লাইসেন্স কী দিন");
        sub.setTextSize(16);
        sub.setTextColor(Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(20));
        root.addView(sub);

        input = new EditText(this);
        input.setHint("AIK-XXXX-XXXX-XXXX-XXXX");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btn = new Button(this);
        btn.setText("সক্রিয় করুন");
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.parseColor("#FF6B6B"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status);

        String msg = getIntent().getStringExtra("msg");
        if (msg != null) status.setText(msg);

        btn.setOnClickListener(v -> activate());
        setContentView(root);
    }

    private void activate() {
        String key = input.getText().toString().trim();
        if (key.isEmpty()) {
            status.setTextColor(Color.parseColor("#FF6B6B"));
            status.setText("লাইসেন্স কী লিখুন");
            return;
        }
        btn.setEnabled(false);
        status.setTextColor(Color.LTGRAY);
        status.setText("যাচাই হচ্ছে...");
        LicenseManager.verify(this, key, r -> {
            if (isFinishing()) return;
            btn.setEnabled(true);
            if (r == LicenseManager.Result.OK) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            status.setTextColor(Color.parseColor("#FF6B6B"));
            if (r == LicenseManager.Result.INVALID) status.setText(LicenseManager.MSG_INVALID);
            else if (r == LicenseManager.Result.OTHER_DEVICE) status.setText(LicenseManager.MSG_OTHER_DEVICE);
            else status.setText(LicenseManager.MSG_OFFLINE);
        });
    }
}
