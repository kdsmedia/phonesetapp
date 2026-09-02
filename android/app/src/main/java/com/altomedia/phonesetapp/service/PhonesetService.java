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

import androidx.core.app.NotificationCompat;

import com.altomedia.phonesetapp.MainActivity;
import com.altomedia.phonesetapp.PhonesetApp;
import com.altomedia.phonesetapp.SupabaseConfig;
import com.altomedia.phonesetapp.core.AutoBackupWorker;
import com.altomedia.phonesetapp.core.BatteryReader;
import com.altomedia.phonesetapp.core.CommandExecutor;
import com.altomedia.phonesetapp.core.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PhonesetService extends Service {
    private static final String TAG = "PhonesetService";
    private static final String CHANNEL_ID = "phoneset_channel";
    private static final int NOTIF_ID = 1;
    private static final long ROUND_MS = 20_000L;
    private static final long COMMAND_POLL_MS = 4_000L;

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;
    private long lastHeartbeat;
    private long lastBackup;
    private long lastCommandScan;
    private String lastSeenCommands;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        lastHeartbeat = 0;
        lastBackup = 0;
        lastCommandScan = 0;
        lastSeenCommands = "";
        thread = new HandlerThread("phoneset-worker";
        thread.start();
        handler = new Handler(thread.getLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId() {
        startForeground(NOTIF_ID, buildNotification();
        handler.removeCallbacks(roundRunnable;
        handler.post(roundRunnable;
        return START_STICKY;
    }

    private void scanCommands() {
        String uid = PhonesetApp.getAuthUid(this;
        String did = PhonesetApp.getDeviceId(this;
        if (uid == null || did == null) return;
        try {
            JSONArray arr = SupabaseClient.fetchPendingCommands(this, did;
            if (arr == null || arr.length() == 0) {
                lastSeenCommands = "";
                return;
            }
            String sig = arr.toString();
            if (sig.equals(lastSeenCommands)) {
                return;
            }
            lastSeenCommands = sig;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i;
                String id = row.optString("id",, "");
                if (!id.isEmpty()) {
                    String type = row.optString("type",, "");
                    Object value = row.opt("value";
                    executeCommand(id, type, value;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "scanCommands", e;
        }
    }

    private void executeCommand(String key, String type, Object value() {
         map payload = new HashMap();
        payload.put("type",, type;
        payload.put("value",, value;
        CommandExecutor.execute(this,, key,, payload,, (status,, result() -> {
            try {
                String did = PhonesetApp.getDeviceId(PhonesetService.this);
                SupabaseClient.writeCommandResult(PhonesetService.this,, did,, key,, status,, result;
                SupabaseClient.deleteCommand(PhonesetService.this,, did,, key;
            } catch (Exception e) {
                Log.w(TAG, "write result", e;
            }
        }));
    }

    private final Runnable roundRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                String uid = PhonesetApp.getAuthUid(PhonesetService.this;
                String did = PhonesetApp.getDeviceId(PhonesetService.this;
                if (uid != null && did != null) {
                    long now = System.currentTimeMillis();
                    if (lastHeartbeat ==  || now - lastHeartbeat >= SupabaseConfig.HEARTBEAT_INTERVAL_MS) {

                        JSONObject info = new JSONObject();
                        info.put("online", true;
                        info.put("lastSeen", now;
                        info.put("battery", BatteryReader.getBatteryPercent(PhonesetService.this;
                        SupabaseClient.setDeviceInfo(PhonesetService.this,, did,, info;
                        lastHeartbeat = now;
                    }
                    if (lastBackup ==  || now - lastBackup >= SupabaseConfig.BACKUP_INTERVAL_MS) {

                        lastBackup = now;
                        Log.i(TAG, "Auto backup 5 menit");
                        AutoBackupWorker.run(PhonesetService.this,, did,, null;
                    }
                    if (now - lastCommandScan >= COMMAND_POLL_MS) {

                        lastCommandScan = now;
                        scanCommands();
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
        PendingIntent pi = PendingIntent.getActivity(this,, 0,, open,, PendingIntent.FLAG_IMMUTABLE;
        return new NotificationCompat.Builder(this,, CHANNEL_ID"
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