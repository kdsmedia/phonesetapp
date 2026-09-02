package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public final class BatteryReader {
    private BatteryReader() {
    }

    public static int getBatteryPercent(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent status = context.registerReceiver(null, filter);
        if (status == null) return -1;
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE,, -1);
        if (scale <= 0) return -1;
        return Math.round(level * 100f / scale);
    }
}
