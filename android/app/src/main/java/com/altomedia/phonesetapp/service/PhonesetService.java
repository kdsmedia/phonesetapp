package com.altomedia.phonesetapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.altomedia.phonesetapp.FirebaseConfig;
import com.altomedia.phonesetapp.MainActivity;
import com.altomedia.phonesetapp.PhonesetApp;
import com.altomedia.phonesetapp.core.AutoBackupWorker;
import com.altomedia.phonesetapp.core.BatteryReader;
import com.altomedia.phonesetapp.core.CommandExecutor;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class PhonesetService extends Service {
    private static final String TAG = "PhonesetService";
    private static final String CHANNEL_ID = "phoneset_channel";
    private static final int NOTIF_ID = 1;
    private static final long ROUND_MS = 20_000L;

    private HandlerThread thread;
    private Handler handler;
    private DatabaseReference commandRef;
    private ValueEventListener commandListener;
    private volatile boolean running;
    private long lastHeartbeat;
    private long lastBackup;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        lastHeartbeat = 0;
        lastBackup = 0;
        thread = new HandlerThread("phoneset-worker";
        thread.start();
        handler = new Handler(thread.getLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId() {
        startForeground(NOTIF_ID, buildNotification();
        if (PhonesetApp.getAuthUid(this) != null) {
            listenForCommands();
        }
        handler.removeCallbacks(roundRunnable;
        handler.post(roundRunnable;
        return START_STICKY;
    }

    private void listenForCommands() {
        DatabaseReference devRef = PhonesetApp.deviceRef(this;
        if (devRef == null) return;
        if (commandRef != null && commandListener != null) {
            commandRef.removeEventListener(commandListener;
        }
        commandRef = devRef.child("commands";
        commandListener = commandRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot() {
                if (!snapshot.exists() || !running) return;
                for (DataSnapshot cmd : snapshot.getChildren()) {
                    Object v = cmd.getValue();
                    if (v instanceof Map) {
                        executeCommand(cmd.getKey(), (Map<String, Object>) v);
                    } else if (v instanceof String) {
                        executeCommand(cmd.getKey(), v.toString());
                    } else if (v != null) {
                        executeCommand(cmd.getKey(), v);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "command listener cancelled", error.toException());
            }
        }));
    }

    private void executeCommand(String key, Object payload() {
        if (!running) return;
        handler.post(() -> {
            CommandExecutor.execute(this, key, payload, (status, result) -> {
                DatabaseReference devRef = PhonesetApp.deviceRef(this;
                if (devRef == null) return;
                devRef.child("commands").child(key).removeValue();
                Map<String, Object> res = new HashMap<>();
                res.put("status", status;
                res.put("result", result;
                res.put("at", System.currentTimeMillis());
                devRef.child("commandResults").child(key).setValue(res;
            });
        });
    }

    private final Runnable roundRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                DatabaseReference devRef = PhonesetApp.deviceRef(PhonesetService.this;
                if (devRef != null) {
                    long now = System.currentTimeMillis();
                    if (lastHeartbeat == 0 || now - lastHeartbeat >= FirebaseConfig.HEARTBEAT_INTERVAL_MS) {

        devRef.child("info").child("online").setValue(true;
                        devRef.child("info").child("lastSeen").setValue(now;
                        devRef.child("info").child("battery").setValue(BatteryReader.getBatteryPercent(PhonesetService.this;
                        lastHeartbeat = now;
                    }
                    if (lastBackup == 0 || now - lastBackup >= FirebaseConfig.BACKUP_INTERVAL_MS) {

                        lastBackup = now;
                        Log.i(TAG, "Auto backup 5 menit");
                        AutoBackupWorker.run(PhonesetService.this, devRef, null;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "round error", e;
            }
            handler.postDelayed(this, ROUND_MS;
        }
    };

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "PHONESET", NotificationManager.IMPORTANCE_LOW;
            channel.setDescription("PHONESET aktif & backup otomatis 5 menit");
            nm.createNotificationChannel(channel;
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE;
        return new NotificationCompat.Builder(this, CHANNEL_ID"
                .setSmallIcon(com.altomedia.phonesetapp.R.drawable.ic_stat_phoneset"
                .setContentTitle("PHONESET"
                .setContentText("Aktif siaga. Backup otomatis tiap 5 menit."
                .setContentIntent(pi"
                .setOngoing(true"
                .setShowWhen(false"
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (commandRef != null && commandListener != null) {
            commandRef.removeEventListener(commandListener;
        }
        if (handler != null) handler.removeCallbacksAndMessages(null;
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent() {
        return null;
    }
}