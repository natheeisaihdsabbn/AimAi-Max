package com.aimai.max;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PinGateActivity extends Activity {
    private EditText pinInput;
    private TextView status;
    private boolean creating;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11, 15, 20));
        getWindow().setNavigationBarColor(Color.rgb(11, 15, 20));
        creating = !PinManager.isConfigured(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(11, 15, 20));

        TextView title = new TextView(this);
        title.setText("AimAi Max");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(212, 175, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView prompt = new TextView(this);
        prompt.setText(creating ? "Create your 4–8 digit PIN" : "Enter your PIN to continue");
        prompt.setTextSize(16);
        prompt.setTextColor(Color.LTGRAY);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(18), 0, dp(16));
        root.addView(prompt);

        pinInput = new EditText(this);
        pinInput.setSingleLine(true);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setHintTextColor(Color.GRAY);
        pinInput.setHint("4–8 digit PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(pinInput, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextColor(Color.rgb(255, 107, 107));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        Button submit = new Button(this);
        submit.setText(creating ? "Save PIN" : "Unlock");
        submit.setAllCaps(false);
        root.addView(submit, new LinearLayout.LayoutParams(-1, -2));
        submit.setOnClickListener(v -> submitPin());

        setContentView(root);
    }

    private void submitPin() {
        String pin = pinInput.getText().toString();
        if (!pin.matches("[0-9]{4,8}")) {
            status.setText("Enter a PIN containing 4 to 8 digits.");
            return;
        }
        if (creating) {
            EditText confirm = new EditText(this);
            confirm.setSingleLine(true);
            confirm.setHint("Confirm PIN");
            confirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            new AlertDialog.Builder(this).setTitle("Confirm your PIN").setView(confirm)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> {
                        if (!pin.equals(confirm.getText().toString())) {
                            status.setText("PINs do not match. Try again.");
                        } else if (PinManager.setPin(this, pin)) {
                            openApp();
                        } else {
                            status.setText("Could not save PIN. Please try again.");
                        }
                    }).show();
        } else if (PinManager.verify(this, pin)) {
            openApp();
        } else {
            status.setText("Incorrect PIN. Try again.");
            pinInput.setText("");
        }
    }

    private void openApp() {
        startActivity(new android.content.Intent(this, MainActivity.class));
        finish();
    }
}
