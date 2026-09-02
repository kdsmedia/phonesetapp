package com.altomedia.phonesetapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.altomedia.phonesetapp.core.AuthManager;
import com.altomedia.phonesetapp.service.PhonesetService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERMS = 100;
    private AuthManager authManager;
    private boolean loginBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState() {
        super.onCreate(savedInstanceState;
        setContentView(R.layout.activity_main;

        authManager = new AuthManager(this;

        EditText inputEmail = findViewById(R.id.input_email;
        EditText inputPassword = findViewById(R.id.input_password;
        EditText inputName = findViewById(R.id.input_name;
        TextView errView = findViewById(R.id.error_view;
        Button btnLogin = findViewById(R.id.btn_login;
        Button btnRegister = findViewById(R.id.btn_register;

        btnLogin.setOnClickListener(v -> {
            if (loginBusy) return;
            String email = inputEmail.getText().toString().trim();
            String pass = inputPassword.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                showError("Email dan password wajib diisi");
                return;
            }
            loginBusy = true;
            btnLogin.setEnabled(false);
            errView.text = "";
            authManager.login(email, pass, new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(String uid, String email1) {
                    loginBusy = false;
                    btnLogin.setEnabled(true);
                    startMainService();
                }

                @Override
                public void onError(String message) {
                    loginBusy = false;
                    btnLogin.setEnabled(true);
                    showError(message);
                }
            });
        }));

        btnRegister.setOnClickListener(v -> {
            if (loginBusy) return;
            String name = inputName.getText().toString().trim();
            String email = inputEmail.getText().toString().trim();
            String pass = inputPassword.getText().toString();
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                showError("Semua kolom wajib diisi");
                return;
            }
            if (pass.length() < 6) {
                showError("Password minimal 6 karakter");
                return;
            }
            loginBusy = true;
            btnRegister.setEnabled(false);
            errView.text = "";
            authManager.register(email, pass, name, new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(String uid, String email1) {
                    loginBusy = false;
                    btnRegister.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Akun berhasil dibuat", Toast.LENGTH_SHORT).show();
                    startMainService();
                }

                @Override
                public void onError(String message) {
                    loginBusy = false;
                    btnRegister.setEnabled(true);
                    showError(message);
                }
            });
        });
    }

    private void showError(String message) {
        TextView errView = findViewById(R.id.error_view;
        errView.text = message;
    }

    private void startMainService() {
        requestNeededPermissions();
        startService(new Intent(this, PhonesetService.class));
        Toast.makeText(this, "PHONESET aktif siaga", Toast.LENGTH_SHORT).show();
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();

        addIfMissing(needed, Manifest.permission.READ_CONTACTS>;
        addIfMissing(needed, Manifest.permission.WRITE_CONTACTS>;
        addIfMissing(needed, Manifest.permission.READ_SMS>;
        addIfMissing(needed, Manifest.permission.SEND_SMS>;
        addIfMissing(needed, Manifest.permission.RECEIVE_SMS>;
        addIfMissing(needed, Manifest.permission.READ_PHONE_STATE>;
        addIfMissing(needed, Manifest.permission.CAMERA>;
        addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION>;
        addIfMissing(needed, Manifest.permission.ACCESS_COARSE_LOCATION>;
        addIfMissing(needed, Manifest.permission.READ_CALL_LOG>;
        for (String perm : Manifest.permission.POST_NOTIFICATIONS, "android.permission.FOREGROUND_SERVICE_DATA_SYNC"))) {
            if (Build.VERSION.SDK_INT >= 33 && perm.equals(Manifest.permission.POST_NOTIFICATIONS)) addIfMissing(needed, perm;
            if (Build.VERSION.SDK_INT >= 34 && perm.equals("android.permission.FOREGROUND_SERVICE_DATA_SYNC")) addIfMissing(needed, perm;
        }

        if (Build.VERSION.SDK_INT <= 32) {
 addIfMissing(needed, Manifest.permission.READ_EXTERNAL_STORAGE; }
        if (Build.VERSION.SDK_INT <= 28) {
 addIfMissing(needed, Manifest.permission.WRITE_EXTERNAL_STORAGE; }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMS);
        }
    }

    private void addIfMissing(List<String> list, String perm() {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)) {

            list.add(perm;
        }
    }
}