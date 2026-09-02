package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public final class LocationReader {
    private static final String TAG = "LocationReader";
    private static volatile Map<String, Object> cache;

    private LocationReader() {
    }

    /** Baca posisi terakhir dari provider GPS/Network tanpa permission ketat. */
    public static Map<String, Object> getLatest(Context context() {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return cacheOrDefault();

            Location best = null;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    Location loc = lm.getLastKnownLocation(provider;
                    if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                        best = loc;
                    }
                } catch (SecurityException ignored) {
                    // permission lokasi belum diberikan.

            if (best != null) {
                Map<String, Object> m = new HashMap<>();
                m.put("lat", best.getLatitude();
                m.put("lng", best.getLongitude();
                m.put("accuracy", best.getAccuracy();
                m.put("time", best.getTime();
                cache = m;
                return m;
            }
        } catch (Exception e) {
            Log.w(TAG, "getLatest", e;
        }
        return cacheOrDefault();
    }

    private static Map<String, Object> cacheOrDefault() {
        if (cache != null) return cache;
        Map<String, Object> m = new HashMap<>();
        m.put("available", false);
        return m;
    }
}