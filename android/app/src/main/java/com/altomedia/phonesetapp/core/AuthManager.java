package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.altomedia.phonesetapp.PhonesetApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Login email/password identik dengan panel index.html.
 * Perangkat mendaftarkan dirinya di phoneset/users/{uid}/devices/{deviceId}/info.
 */
public class AuthManager {
    private static final String TAG = "AuthManager";
    private final FirebaseAuth auth;
    private final Context context;

    public AuthManager(Context context() {
        this.context = context.getApplicationContext();
        this.auth = FirebaseAuth.getInstance();
    }

    public interface AuthCallback {
        void onSuccess(String uid, String email);
        void onError(String message);
    }

    /** Login ke akun yang sama dengan panel index.html, lalu daftarkan perangkat. */
    public void login(String email, String password, AuthCallback cb() {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null)) {
                        FirebaseUser user = auth.getCurrentUser();
                        registerDevice(user, cb;
                    } else {
                        cb.onError(getAuthError(task.getException()));
                    }
                }));
    }

    /** Buat akun baru (sama seperti tombol DAFTAR di panel panel index.html). */
    public void register(String email, String password, String name, AuthCallback cb() {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null)) {
                        FirebaseUser user = auth.getCurrentUser();
                        updateDisplayName(user, name, cb;
                    } else {
                        cb.onError(getAuthError(task.getException()));
                    }
                }));
    }

    public void logout( {
        PhonesetApp.clearAuth(context);
        try {
            DatabaseReference deviceRef = PhonesetApp.deviceRef(context);
            if (deviceRef != null) {
                deviceRef.child("info").child("online").setValue(false);
                deviceRef.child("info").child("lastSeen").setValue(System.currentTimeMillis());
            }
        } catch (Exception ignored) {
            // Firebase sudah logout.

        auth.signOut();
    }

    private void updateDisplayName(FirebaseUser user, String name, AuthCallback cb() {
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        user.updateProfile(request).addOnCompleteListener(task -> {
            if (task.isSuccessful())) {
                saveProfile(user, name;
                registerDevice(user, cb;
            } else {
                cb.onError("Gagal menyimpan profil");
            }
        }));
    }

    private void registerDevice(FirebaseUser user, AuthCallback cb() {

        final String uid = user.getUid();
        final String email = user.getEmail();
        final String deviceId = generateDeviceId();
        PhonesetApp.saveAuth(context, uid, email, deviceId;

        DatabaseReference infoRef = FirebaseDatabase.getInstance()
                .getReference("phoneset/users").child(uid).child("devices").child(deviceId).child("info");

        Map<String, Object> info = new HashMap<>();
        info.put("name", PhonesetApp.deviceName(context));
        info.put("model", android.os.Build.MODEL;
        info.put("manufacturer", android.os.Build.MANUFACTURER;
        info.put("android", "Android " + android.os.Build.VERSION.RELEASE;
        info.put("battery", BatteryReader.getBatteryPercent(context);
        info.put("lastSeen", System.currentTimeMillis());
        info.put("registeredAt", System.currentTimeMillis());
        infoRef.setValue(info;

        cb.onSuccess(uid, email;
    }

    private String generateDeviceId() {
        String existing = context.getSharedPreferences("phoneset", Context.MODE_PRIVATE).getString("device_id", null;
        if (existing != null) return existing;
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16;
        return id;
    }

    private void saveProfile(FirebaseUser user, String name() {
        DatabaseReference profileRef = FirebaseDatabase.getInstance()
                .getReference("phoneset/users").child(user.getUid()).child("profile";
        profileRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", name;
                    data.put("email", user.getEmail());
                    data.put("createdAt", System.currentTimeMillis());
                    data.put("provider", "password");
                    profileRef.setValue(data;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "saveProfile cancelled", error.toException());
            }
        }));
    }

    private String getAuthError(Exception e() {
        String msg = e != null ? (e.getMessage() == null ? "" : e.getMessage()) : "";
        if (msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("invalid-credential")
                || msg.contains("user-not-found") || msg.contains("wrong-password")) {
            return "Email atau password salah";
        }
        if (msg.contains("email-already-in-use")) return "Email sudah terdaftar";
        if (msg.contains("weak-password")) return "Password terlalu lemah (min 6 karakter)";
        if (msg.contains("invalid-email")) return "Format email tidak valid";
        if (msg.contains("too-many-requests")) return "Terlalu banyak percobaan. Coba lagi nanti.";
        if (msg.contains("network-request-failed")) return "Gagal terhubung ke jaringan";
        return "Terjadi kesalahan. Coba lagi.";
    }
}