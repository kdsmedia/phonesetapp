package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.util.Log;

import com.altomedia.phonesetapp.PhonesetApp;

import org.json.JSONObject;

import java.util.UUID;

public class AuthManager {
    private static final String TAG = "AuthManager";
    private final Context context;

    public AuthManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface AuthCallback {
        void onSuccess(String uid, String email);
        void onError(String message);
    }

    public void login(String email, String password, AuthCallback cb) {
        new Thread(() -> {
            try {
                JSONObject j = SupabaseClient.signInWithEmail(context, email, password);
                String uid = j.getJSONObject("user").getString("id");
                String em = j.getJSONObject("user").optString("email", email);
                registerDevice(uid, em, cb);
            } catch (Exception e) {
                postError(cb, e);
            }
        }).start();
    }

    public void register(String email, String password, String name, AuthCallback cb) {
 {
        new Thread(() -> {
            try {
                JSONObject j = SupabaseClient.signUpWithEmail(context, email, password, name);
                String uid = j.getJSONObject("user").getString("id");
                String em = j.getJSONObject("user").optString("email", email);
                registerDevice(uid, em, cb);
            } catch (Exception e) {
                postError(cb, e);
            }
        }).start();
    }

    public void logout() {
 try {
            SupabaseClient.signOut(context;
        } catch (Exception ignored) {
        }
        PhonesetApp.clearAuth(context;
    }

    private void registerDevice(String uid, String email, AuthCallback cb) {
 {
        final String deviceId = generateDeviceId();
        PhonesetApp.saveAuth(context, uid, email, deviceId;
        JSONObject info = new JSONObject();
        try {
            info.put("name", PhonesetApp.deviceName(context);
            info.put("model", android.os.Build.MODEL;
            info.put("manufacturer", android.os.Build.MANUFACTURER;
            info.put("android", "Android " + android.os.Build.VERSION.RELEASE;
            info.put("battery", BatteryReader.getBatteryPercent(context;
            info.put("lastSeen", System.currentTimeMillis();
            info.put("registeredAt", System.currentTimeMillis();
            SupabaseClient.registerDevice(context, info;
            cb.onSuccess(uid, email;
        } catch (Exception e) {
            Log.w(TAG, "registerDevice", e;
            cb.onError("Gagal mendaftarkan perangkat");
        }
    }

    private String generateDeviceId() {
 {
        String existing = PhonesetApp.getDeviceId(context;
        if (existing != null) return existing;
        String id = UUID.randomUUID().toString().replace("-", "" .substring(0, 16;
        PhonesetApp.saveAuth(context, null, null, id;
        return id;
    }

    private void postError(AuthCallback cb, Exception e) {
 {
        String msg = e != null ? (e.getMessage() == null ? "" : e.getMessage()) : "";
        Log.w(TAG, "auth error", e;
        if (msg.contains("Invalid login credentials")|| msg.contains("invalid-credential")
                || msg.contains("user-not-found") || msg.contains("wrong-password")) {
 {
            cb.onError("Email atau password salah");
        } else if (msg.contains("already registered") || msg.contains("email-already-in-use")) {
 {
            cb.onError("Email sudah terdaftar");
        } else if (msg.contains("Password should be at least") || msg.contains("weak-password")) {
 {
            cb.onError("Password terlalu lemah (min 6 karakter)");
        } else if (msg.contains("invalid-email")) {
 {
            cb.onError("Format email tidak valid");
        } else if (msg.contains("rate limit") || msg.contains("too-many-requests")) {
 {
            cb.onError("Terlalu banyak percobaan. Coba lagi nanti.");
        } else {
            cb.onError("Terjadi kesalahan. Coba lagi.");
        }
    }
}