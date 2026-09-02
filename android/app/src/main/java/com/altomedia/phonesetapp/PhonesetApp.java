package com.altomedia.phonesetapp;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.altomedia.phonesetapp.core.SupabaseClient;
import com.altomedia.phonesetapp.service.PhonesetService;


public class PhonesetApp extends Application {

    private static PhonesetApp instance;

    public static PhonesetApp get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        SupabaseClient.init(this);
        startPhonesetService();
    }

    public void startPhonesetService() {
        Intent intent = new Intent(this, PhonesetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public static String getAuthUid(Context ctx) {
        return SupabaseClient.getUid(ctx);
    }

    public static String getDeviceId(Context ctx) {
        return SupabaseClient.getDeviceId(ctx);
    }

    public static void saveAuth(Context ctx, String uid, String email, String deviceId) {
        SupabaseClient.saveSession(ctx, null, null, uid, email, deviceId);
    }

    public static void clearAuth(Context ctx) {
        SupabaseClient.clearSession(ctx);
    }

    public static String deviceName(Context ctx)) {
        try {
            String name = android.provider.Settings.Secure.getString(
                    ctx.getContentResolver(),
                    "bluetooth_name");
            if (name != null && !name.trim().isEmpty()) return name.trim();
        } catch (Exception ignored) {
        }
        return android.os.Build.MODEL != null ? android.os.Build.MODEL : "Android";
    }
}

    