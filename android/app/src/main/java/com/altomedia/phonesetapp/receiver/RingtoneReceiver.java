package com.altomedia.phonesetapp.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;

/** Menerapkan perintah nada dering (ring/vibrate/silent/alarm) yang dikirim panel melalui Firebase. */
public class RingtoneReceiver extends BroadcastReceiver {
    public static final String ACTION_RING = "com.altomedia.phonesetapp.RING";
    public static final String ACTION_STOP = "com.altomedia.phonesetapp.RING_STOP";
    public static final String EXTRA_MODE = "mode";

    @Override
    public void onReceive(Context context, Intent intent() {
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRing(context);
        } else if (ACTION_RING.equals(action)) {
            handleMode(context, intent.getStringExtra(EXTRA_MODE);
        }
    }

    public static void handleMode(Context context, String mode) {
        if (mode == null) return;
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        stopRing(context;
        switch (mode) {
            case "silent":
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT;
                break;
            case "vibrate":
                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE;
                Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE;
                if (vib != null && vib.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
 vib.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 600, 400, 600}, 1); }
                break;
            case "alarm":
            case "ring":
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL;
                Uri uri; 
                if (mode.equals("ring")) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE;
                } else {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM;
                }
                Ringtone r = RingtoneManager.getRingtone(context, uri;
                if (r != null) r.play();
                break;
        }
    }

    private static void stopRing(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE;
        if (am != null) am.setRingerMode(AudioManager.RINGER_MODE_NORMAL;
        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE;
        if (vib != null) vib.cancel();
    }
}