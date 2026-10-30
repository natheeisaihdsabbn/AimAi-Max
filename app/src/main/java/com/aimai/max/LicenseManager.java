package com.aimai.max;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.List;

public final class LicenseManager {
    // অ্যাপের গোপন salt। একবার বসানোর পর বদলাবে না, বদলালে সব ফোনের হ্যাশ অকেজো হবে।
    private static final String SALT = "6beab047bbc97ec8512183932e6ab5ab";

    private static final long GRACE_MS = 48L * 60 * 60 * 1000;
    private static final long RECHECK_MS = 3L * 60 * 60 * 1000;
    private static final String PREFS = "aimai_license";
    private static final String K_KEY = "key";
    private static final String K_LAST = "last_verified";

    public static final String MSG_INVALID = "কী ভুল বা বাতিল";
    public static final String MSG_OTHER_DEVICE = "এই কী অন্য ফোনে ব্যবহৃত হচ্ছে";
    public static final String MSG_OFFLINE = "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যাচ্ছে না। আবার চেষ্টা করুন।";
    public static final String MSG_EXPIRED = "অনেকক্ষণ যাচাই করা হয়নি। ইন্টারনেট চালু করে আবার লাইসেন্স দিন।";

    public enum Result { OK, INVALID, OTHER_DEVICE, OFFLINE }

    public interface Callback { void onResult(Result r); }

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable loop;

    private LicenseManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasValidLocalLicense(Context c) {
        SharedPreferences p = prefs(c);
        String key = p.getString(K_KEY, null);
        if (key == null || key.isEmpty()) return false;
        long elapsed = System.currentTimeMillis() - p.getLong(K_LAST, 0L);
        return elapsed >= 0 && elapsed <= GRACE_MS;
    }

    private static void save(Context c, String key) {
        prefs(c).edit().putString(K_KEY, key).putLong(K_LAST, System.currentTimeMillis()).apply();
    }

    private static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static String deviceHash(Context c) {
        try {
            String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id == null) id = "unknown";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((id + ":" + SALT).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    public static void verify(Context ctx, String key, Callback cb) {
        final Context app = ctx.getApplicationContext();
        if (key == null || key.isEmpty() || key.contains("/")) {
            cb.onResult(Result.INVALID);
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            fetch(app, key, cb);
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(t -> {
            if (t.isSuccessful()) fetch(app, key, cb);
            else cb.onResult(Result.OFFLINE);
        });
    }

    private static void fetch(Context app, String key, Callback cb) {
        DocumentReference ref = FirebaseFirestore.getInstance().collection("licenses").document(key);
        ref.get(Source.SERVER).addOnCompleteListener(t -> {
            if (!t.isSuccessful()) {
                cb.onResult(isDenied(t.getException()) ? Result.INVALID : Result.OFFLINE);
                return;
            }
            DocumentSnapshot d = t.getResult();
            if (d == null || !d.exists() || !Boolean.TRUE.equals(d.getBoolean("active"))) {
                cb.onResult(Result.INVALID);
                return;
            }
            String hash = deviceHash(app);
            Object devices = d.get("devices");
            if (devices instanceof List && ((List<?>) devices).contains(hash)) {
                save(app, key);
                cb.onResult(Result.OK);
                return;
            }
            ref.update("devices", FieldValue.arrayUnion(hash)).addOnCompleteListener(u -> {
                if (u.isSuccessful()) {
                    save(app, key);
                    cb.onResult(Result.OK);
                } else if (isDenied(u.getException())) {
                    cb.onResult(Result.OTHER_DEVICE);
                } else {
                    cb.onResult(Result.OFFLINE);
                }
            });
        });
    }

    public static void startPeriodicCheck(Activity a) {
        final WeakReference<Activity> ref = new WeakReference<>(a);
        if (loop != null) H.removeCallbacks(loop);
        loop = new Runnable() {
            @Override public void run() {
                Activity act = ref.get();
                if (act == null || act.isFinishing()) return;
                recheck(act);
                H.postDelayed(this, RECHECK_MS);
            }
        };
        H.post(loop);
    }

    private static void recheck(Activity act) {
        String key = prefs(act).getString(K_KEY, null);
        if (key == null) {
            kick(act, MSG_INVALID);
            return;
        }
        verify(act, key, r -> {
            if (act.isFinishing()) return;
            if (r == Result.INVALID) {
                clear(act);
                kick(act, MSG_INVALID);
            } else if (r == Result.OTHER_DEVICE) {
                clear(act);
                kick(act, MSG_OTHER_DEVICE);
            } else if (r == Result.OFFLINE && !hasValidLocalLicense(act)) {
                kick(act, MSG_EXPIRED);
            }
        });
    }

    private static void kick(Activity act, String msg) {
        Intent i = new Intent(act, LicenseActivity.class);
        i.putExtra("msg", msg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        act.startActivity(i);
        act.finish();
    }
}
