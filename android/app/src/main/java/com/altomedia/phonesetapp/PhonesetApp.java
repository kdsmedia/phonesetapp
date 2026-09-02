package com.altomedia.phonesetapp;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.altomedia.phonesetapp.service.PhonesetService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class PhonesetApp extends Application {

    private static PhonesetApp instance;

    public static PhonesetApp get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }
        startPhonesetService();
    }

    public void startPhonesetService() {
        Intent intent = new Intent(this, PhonesetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public static String getAuthUid(Context ctx) {
        return ctx.getSharedPreferences("phoneset", Context.MODE_PRIVATE).getString("auth_uid", null);
    }

    public static String getDeviceId(Context ctx) {
        return ctx.getSharedPreferences("phoneset", Context.MODE_PRIVATE).getString("device_id", null);
    }

    public static void saveAuth(Context ctx, String uid, String email, String deviceId) {
        ctx.getSharedPreferences("phoneset", Context.MODE_PRIVATE).edit()
                .putString("auth_uid", uid)
                .putString("auth_email", email)
                .putString("device_id", deviceId)
                .apply();
    }

    public static void clearAuth(Context ctx) {
        ctx.getSharedPreferences("phoneset", Context.MODE_PRIVATE).edit()
                .remove("auth_uid")
                .remove("auth_email")
                .remove("device_id")
                .apply();
    }

    public static DatabaseReference deviceRef(Context ctx) {
        String uid = getAuthUid(ctx);
        String did = getDeviceId(ctx);
        if (uid == null || did == null) return null;
        return FirebaseDatabase.getInstance()
                .getReference("phoneset/users").child(uid).child("devices").child(did);
    }
}