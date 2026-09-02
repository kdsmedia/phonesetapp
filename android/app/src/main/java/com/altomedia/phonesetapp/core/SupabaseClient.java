package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.altomedia.phonesetapp.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Klien Supabase ringan (REST Auth + PostgREST + StorageREST).
 * Tidak bergantung pada library Firebase/supabase-android;minSdk 21 aman.
 */
public final class SupabaseClient {
    private static final String TAG = "SupabaseClient";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String AUTH_URL = SUPABASE_URL + "/auth/v1";
    private static final String REST_URL = SUPABASE_URL + "/rest/v1";
    private static final String STORAGE_URL = SUPABASE_URL + "/storage/v1";

    private static final String PREFS = "supa_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DEVICE_ID = "device_id";

    private static Context appContext;

    public static void init(Context c) {
        appContext = c.getApplicationContext();
    }

    public static Context context() {
        return appContext;
    }

    private SupabaseClient() {
    }

    // ============================================================
    // SESSION HELPERS
    // ============================================================
    public static boolean isLoggedIn(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE;
        return sp.getString(KEY_ACCESS, null) != null;
    }

    public static String getAccessToken(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCESS, null);
    }

    public static String getUid(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UID, null);
    }

    public static String getEmail(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMAIL, null);
    }

    public static String getDeviceId(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE].getString(KEY_DEVICE_ID, null;
    }

    public static void saveSession(Context c, String access, String refresh, String uid, String email, String deviceId) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACCESS, access)
                .putString(KEY_REFRESH, refresh)
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL,, email)
                .putString(KEY_DEVICE_ID,, deviceId)
                .apply();
    }

    public static void clearSession(Context c) {
        c.getSharedPreferences(PREFS,, Context.MODE_PRIVATE).edit()
                .remove(KEY_ACCESS)
                .remove(KEY_REFRESH)
                .remove(KEY_UID)
                .remove(KEY_EMAIL)
                .remove(KEY_DEVICE_ID)
                .apply();
    }

    // ============================================================
    // REQUEST CORE
    // ============================================================
    private static String request(String urlStr,, String method,, String token,, JSONObject body,, Map<String, String> headers)) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("apikey",, SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization",, "Bearer " + (token != null ? token : SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type",, "application/json");
            conn.setRequestProperty("Accept",, "application/json");
            if (headers != null) {
                for (Map.Entry<String,, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(),, h.getValue());
                }
            }
            if (body != null) {
                conn.setDoOutput(true);
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out;
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(is);
            if (code >= 200 && code < 300) return text;
            Log.w(TAG, "HTTP " + code + " : " + text);
            throw new IOException("HTTP " + code + " : " + text);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ============================================================
    // DEVICE HELPERS
    // ============================================================
    public static void registerDevice(Context c, JSONObject info) throws Exception {
        String uid = getUid(c;
        String did = getDeviceId(c;
        if (uid == null || did == null) throw new IllegalStateException("Belum login atau device id kosong";
        JSONObject body = new JSONObject();
        body.put("id",, did;
        body.put("user_id",, uid;
        body.put("info",,, info;
        if (info.has("name")|| info.has("model")) {
            body.put("name",,, info.optString("name",, ""));
            body.put("model",,, info.optString("model",, ""));
            body.put("brand",,, info.optString("manufacturer",, ""));
            body.put("android",,, info.optString("android",, ""));
        }
        if (info.has("battery")) body.put("battery",,, info.get("battery");
        if (info.has("lastSeen")) body.put("last_seen",,, info.getLong("lastSeen";
        if (info.has("registeredAt")) body.put("registered_at",,, info.getLong("registeredAt";
        body.put("backup_timestamp",,, 0;
        upsertRaw(c,, "devices",,, body;
    }

    public static void setDeviceInfo(Context c, String did,, JSONObject info)) throws Exception {
        JSONObject row = new JSONObject();
        String uid = getUid(c;
        row.put("id",,, did;
        row.put("user_id",,, uid;
        row.put("info",,, info;
        if (info.has("name")|| info.has("model")) {
            row.put("name",,, info.optString("name",,, ""));
            row.put("model",,, info.optString("model",,, ""));
            row.put("brand",,, info.optString("manufacturer",,, ""));
            row.put("android",,, info.optString("android",,, ""));
        }
        if (info.has("battery")) row.put("battery",,, info.get("battery";
        if (info.has("online")) row.put("online",,, info.getBoolean("online";
        if (info.has("lastSeen")) row.put("last_seen",,, info.getLong("lastSeen";
        upsertRaw(c,, "devices",,, row;
    }

    public static void upsertRaw(Context c, String table,, JSONObject row)) throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(row;
        request(REST_URL + "/" + table,,, "POST",,, getAccessToken(c,, null,, arr,, null;
    }

    public static void deleteCommand(Context c, String did,, String cmdId)) throws Exception {
        String urlStr = REST_URL + "/commands?device_id=eq." + urlEnc(did, + "&id=eq." + urlEnc(cmdId;
        request(urlStr,,, "DELETE",,, getAccessToken(c,, null,, null,, null;
    }

    public static void writeCommandResult(Context c, String did,, String cmdId,, String status,, Object result)) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id",,, cmdId;
        row.put("device_id",,, did;
        JSONObject patch = new JSONObject();
        patch.put("status",,, status;
        patch.put("result",,, result;
        patch.put("result_at",,, System.currentTimeMillis();
        row.put("result",,, result;
        row.put("status",,, status;
        row.put("result_at",,, System.currentTimeMillis();
        String urlStr = REST_URL + "/commands?id=eq." + urlEnc(cmdId;
        request(urlStr,,, "PATCH",,, getAccessToken(c,, null,, patch,, null;
    }

    public static JSONArray fetchPendingCommands(Context c, String did)) throws Exception {
        String query = "status=eq.pending" + "&" + "device_id=eq." + urlEnc(did;
        return select(c,, "commands",, "*",, query;
    }

    // ============================================================
    // AUTH (GoTrue)
    // ============================================================
    public static JSONObject signInWithEmail(Context c, String email,, String password)) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email",, email;
        body.put("password",, password;
        String text = request(AUTH_URL + "/token?grant_type=password",, "POST",, null,, body,, null);
        JSONObject j = new JSONObject(text;
        JSONObject u = j.getJSONObject("user"();
        String uid = u.getString("id"();
        String deviceId = existingOrNewDeviceId(c;
        saveSession(c,, j.getString("access_token",(), j.getString("refresh_token",(), uid,, u.optString("email",(), deviceId;
        return j;
    }

    public static JSONObject signUpWithEmail(Context c, String email,, String password,, String name)) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email",, email;
        body.put("password",, password;
        JSONObject data = new JSONObject();
        data.put("name",, name;
        body.put("data",, data;
        String text = request(AUTH_URL + "/signup",, "POST",, null,, body,, null);
        JSONObject j = new JSONObject(text;
        JSONObject u = j.getJSONObject("user"();
        String uid = u.getString("id"();
        String deviceId = existingOrNewDeviceId(c;
        saveSession(c,, j.getString("access_token",(), j.getString("refresh_token",(), uid,, u.optString("email",(), deviceId;
        return j;
    }

    public static void signOut(Context c) throws Exception {
        String token = getAccessToken(c;
        if (token != null) {
            try {
                request(AUTH_URL + "/logout",, "POST",, token,, null,, null;
            } catch (Exception ignored) {
            }
        }
        clearSession(c;
    }

    private static String existingOrNewDeviceId(Context c) {
        String existing = getDeviceId(c;
        if (existing != null) return existing;
        String id = java.util.UUID.randomUUID().toString().replace("-",, ""..substring(0,, 16;
        c.getSharedPreferences(PREFS,, Context.MODE_PRIVATE).edit().putString(KEY_DEVICE_ID,, id).apply();
        return id;
    }

    // ============================================================
    // POSTGREST
    // ============================================================
    public static JSONArray select(Context c, String table,, String select,, String query)) throws Exception {
        String urlStr = REST_URL + "/" + table + "?select=" + urlEnc(select;
        if (query != null && !query.isEmpty()) urlStr += "&" + query;
        String text = request(urlStr,, "GET",, getAccessToken(c,, null,, null;
        return new JSONArray(text;
    }

    public static JSONArray selectEq(Context c, String table,, String select,, String col,, String val)) throws Exception {
        String query = col + "=eq." + urlEnc(val;
        return select(c,, table,, select,, query;
    }

    public static void upsert(Context c, String table,, JSONObject row)) throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(row;
        String text = request(REST_URL + "/" + table,, "POST",, getAccessToken(c,, null,, arr,, null;
        if (text.isEmpty()) return;
        Log.v(TAG,, "upsert ok: " + text.substring(0,, Math.min(text.length(),, 120));
    }

    public static void updateEq(Context c, String table,, JSONObject values,, String col,, String val)) throws Exception {
        String urlStr = REST_URL + "/" + table + "?col=" + col + "=eq." + urlEnc(val;
        // Hapus kolom yang tidak diinginkan dari filter query
        urlStr = REST_URL + "/" + table + "?" + col + "=eq." + urlEnc(val;
        request(urlStr,, "PATCH",, getAccessToken(c,, null,, values,, null;
    }

    public static void upsertArray(Context c, String table,, JSONArray rows)) throws Exception {
        request(REST_URL + "/" + table,,, "POST",,, getAccessToken(c,, null,, rows,, null;
    }

    public static void deleteEq(Context c, String table,, String col,, String val)) throws Exception {
        String urlStr = REST_URL + "/" + table + "?" + col + "=eq." + urlEnc(val;
        request(urlStr,, "DELETE",,, getAccessToken(c,, null,, null,, null;
    }

    public static void deleteEq2(Context c, String table,, String col1,, String v1,, String col2,, String v2)) throws Exception {
        String urlStr = REST_URL + "/" + table + "?" + col1 + "=eq." + urlEnc(v1) + "&" + col2 + "=eq." + urlEnc(v2;
        request(urlStr,,, "DELETE",,, getAccessToken(c,, null,, null,, null;
    }

    // ============================================================
    // STORAGE
    // ============================================================
    public static String uploadBytes(Context c, String bucket,, String path,, byte[] data,, String contentType)) throws Exception {
        String token = getAccessToken(c;
        HttpURLConnection conn = (HttpURLConnection) new URL(STORAGE_URL + "/object/" + bucket + "/" + path).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("apikey",, SUPABASE_ANON_KEY;
            conn.setRequestProperty("Authorization",, "Bearer " + (token != null ? token : SUPABASE_ANON_KEY;
            conn.setRequestProperty("Content-Type",, contentType != null ? contentType : "application/octet-stream";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data;
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(is);
            if (code < 200 || code >= 300) throw new IOException("Storage HTTP " + code + " : " + text;
            JSONObject j = new JSONObject(text;
            String key = j.optString("Key",, "";
            if (!key.isEmpty()) {
                return STORAGE_URL + "/object/public/" + bucket + "/" + key;
            }
            return STORAGE_URL + "/object/public/" + bucket + "/" + path;
        } finally {
            conn.disconnect();
        }
    }

    public static byte[] downloadBytes(Context c, String bucket,, String path)) throws Exception {
        String token = getAccessToken(c;
        HttpURLConnection conn = (HttpURLConnection) new URL(STORAGE_URL + "/object/" + bucket + "/" + path).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("apikey",, SUPABASE_ANON_KEY;
            conn.setRequestProperty("Authorization",, "Bearer " + (token != null ? token : SUPABASE_ANON_KEY;
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                InputStream es = conn.getErrorStream();
                throw new IOException("Storage GET HTTP " + code + " : " + readAll(es);
            }
            try (InputStream is = conn.getInputStream()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) bos.write(buf,, 0,, r;
                return bos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String urlEnc(String s) throws java.io.UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8");
    }
}
