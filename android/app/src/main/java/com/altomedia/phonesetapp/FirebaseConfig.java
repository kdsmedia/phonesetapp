package com.altomedia.phonesetapp;

/**
 * Konfigurasi Firebase - identik dengan panel index.html sehingga perangkat
 * dan panel membaca/menulis data yang sama secara real-time.
 */
public final class FirebaseConfig {
    private FirebaseConfig() {}

    public static final String API_KEY = "AIzaSyDGmV5ZIVsR38zyLCQ6dbtP1Fw9dV36qRk";
    public static final String AUTH_DOMAIN = "altomedia-8f793.firebaseapp.com";
    public static final String DATABASE_URL = "https://altomedia-8f793-default-rtdb.asia-southeast1.firebasedatabase.app";
    public static final String PROJECT_ID = "altomedia-8f793";
    public static final String STORAGE_BUCKET = "altomedia-8f793.firebasestorage.app";
    public static final String MESSAGING_SENDER_ID = "327513974065";
    public static final String APP_ID = "1:327513974065:android:d5be38ffef1bbd3f91bc10";

    /** Auto backup data setiap 5 menit */
    public static final long BACKUP_INTERVAL_MS = 5 * 60 * 1000L;

    /** Heartbeat lastSeen - sama dengan toleransi status online di panel (5 menit) */
    public static final long HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L;
}