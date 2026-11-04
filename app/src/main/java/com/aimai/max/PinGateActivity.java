package com.aimai.max;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local PIN gate. The existing Firebase license verification remains in MainActivity. */
public class PinGateActivity extends Activity {
    private static final String PREFS = "aimai_local_pin";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final int ITERATIONS = 120000;

    private EditText pinInput;
    private TextView status;
    private boolean setupMode;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,15,20));
        getWindow().setNavigationBarColor(Color.rgb(11,15,20));
        setupMode = !hasPin();
        buildScreen();
    }

    private boolean hasPin() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.contains(KEY_SALT) && p.contains(KEY_HASH);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(11,15,20));

        TextView title = new TextView(this);
        title.setText("AimAi Max");
        title.setTextColor(Color.rgb(212,175,55));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView prompt = new TextView(this);
        prompt.setText(setupMode ? "Create your app PIN" : "Enter your PIN");
        prompt.setTextColor(Color.WHITE);
        prompt.setTextSize(17);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(18), 0, dp(18));
        root.addView(prompt);

        pinInput = new EditText(this);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setHintTextColor(Color.GRAY);
        pinInput.setHint("4–8 digit PIN");
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setSingleLine(true);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(pinInput, new LinearLayout.LayoutParams(-1, -2));

        Button submit = new Button(this);
        submit.setText(setupMode ? "Save PIN" : "Unlock");
        root.addView(submit, new LinearLayout.LayoutParams(-1, -2));

        if (setupMode) {
            EditText confirm = new EditText(this);
            confirm.setTextColor(Color.WHITE);
            confirm.setHintTextColor(Color.GRAY);
            confirm.setHint("Confirm PIN");
            confirm.setGravity(Gravity.CENTER);
            confirm.setSingleLine(true);
            confirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            root.addView(confirm, 1, new LinearLayout.LayoutParams(-1, -2));
            submit.setOnClickListener(v -> saveNewPin(confirm.getText().toString()));
        } else {
            submit.setOnClickListener(v -> unlock());
        }

        status = new TextView(this);
        status.setTextColor(Color.rgb(255,107,107));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, 0);
        root.addView(status);
        setContentView(root);
    }

    private String hash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try {
            byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            StringBuilder out = new StringBuilder();
            for (byte b : result) out.append(String.format("%02x", b));
            return out.toString();
        } finally { spec.clearPassword(); }
    }

    private boolean validPin(String pin) { return pin != null && pin.matches("[0-9]{4,8}"); }

    private void saveNewPin(String confirmation) {
        String pin = pinInput.getText().toString();
        if (!validPin(pin)) { status.setText("Enter a PIN with 4 to 8 digits."); return; }
        if (!pin.equals(confirmation)) { status.setText("PINs do not match."); return; }
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            StringBuilder encodedSalt = new StringBuilder();
            for (byte b : salt) encodedSalt.append(String.format("%02x", b));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_SALT, encodedSalt.toString())
                    .putString(KEY_HASH, hash(pin, salt)).apply();
            openApp();
        } catch (Exception e) { status.setText("Could not save PIN. Please try again."); }
    }

    private void unlock() {
        String pin = pinInput.getText().toString();
        if (!validPin(pin)) { status.setText("Enter your 4 to 8 digit PIN."); return; }
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            byte[] salt = new byte[16];
            String hex = p.getString(KEY_SALT, "");
            if (hex.length() != 32) { status.setText("PIN data is unavailable. Contact app support."); return; }
            for (int i=0;i<16;i++) salt[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
            byte[] a = hash(pin, salt).getBytes("UTF-8");
            byte[] b = p.getString(KEY_HASH, "").getBytes("UTF-8");
            if (MessageDigest.isEqual(a,b)) openApp();
            else status.setText("Incorrect PIN. Try again.");
        } catch (Exception e) { status.setText("Could not verify PIN. Please try again."); }
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}