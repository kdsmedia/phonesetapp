package com.altomedia.phonesetapp;

/**
 * Konfigurasi Supabase - identik dengan panel index.html sehingga perangkat
 * dan panel membaca/menulis data yang sama secara real-time.
 */
public final class SupabaseConfig {
    private SupabaseConfig() {}

    /** Auto backup data setiap 5 menit */
    public static final long BACKUP_INTERVAL_MS =5 * 60 * 1000L;

    /** Heartbeat lastSeen - sama dengan toleransi status online di panel (5 menit) */
    public static final long HEARTBEAT_INTERVAL_MS =5 * 60 * 1000L;
}