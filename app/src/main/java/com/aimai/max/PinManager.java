package com.aimai.max;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinManager {
    private static final String PREFS = "aimai_pin_security";
    private static final String SALT = "pin_salt";
    private static final String HASH = "pin_hash";
    private static final int ITERATIONS = 120000;
    private static final int KEY_BITS = 256;

    private PinManager() {}

    public static boolean isConfigured(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(HASH);
    }

    public static boolean setPin(Context context, String pin) {
        if (!valid(pin)) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(pin, salt);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verify(Context context, String pin) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saltText = prefs.getString(SALT, null);
        String hashText = prefs.getString(HASH, null);
        if (saltText == null || hashText == null || pin == null) return false;
        try {
            byte[] expected = Base64.decode(hashText, Base64.NO_WRAP);
            byte[] actual = derive(pin, Base64.decode(saltText, Base64.NO_WRAP));
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean valid(String pin) {
        return pin != null && pin.matches("[0-9]{4,8}");
    }
}
