package com.example.displayguardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private Button toggle;
    private static final String PREFS = "guardian";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HASH = "password_hash";
    private static final String KEY_SALT = "password_salt";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.toggleButton);
        updateUi();

        toggle.setOnClickListener(v -> {
            if (prefs.getBoolean(KEY_ENABLED, false)) {
                showPasswordDialog(true);
            } else {
                if (!hasOverlayPermission()) {
                    Toast.makeText(this, "Allow Display Guardian to display over other apps.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    return;
                }
                if (!hasPassword()) showPasswordDialog(false);
                else enableGuardian();
            }
        });
    }

    private void enableGuardian() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        ContextCompat.startForeground(this, new Intent(this, GuardianService.class));
        updateUi();
        Toast.makeText(this, "Display Guardian ON", Toast.LENGTH_SHORT).show();
        if (!isAccessibilityEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("One permission needed")
                .setMessage("To know when you are actually using other apps, Display Guardian uses Android Accessibility. Without it, the 15-second idle timer cannot reliably work across the whole phone.")
                .setPositiveButton("Open Accessibility", (d,w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Later", null).show();
        }
    }

    private void showPasswordDialog(boolean disabling) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Password");
        new AlertDialog.Builder(this)
            .setTitle(disabling ? "Password required" : "Set password")
            .setMessage(disabling ? "Enter your Display Guardian password to turn protection OFF." : "This password unlocks the dim protection screen and turns the feature OFF.")
            .setView(input)
            .setPositiveButton(disabling ? "TURN OFF" : "SET & TURN ON", (d,w) -> {
                String p = input.getText().toString();
                if (p.length() < 4) { Toast.makeText(this, "Use at least 4 characters.", Toast.LENGTH_SHORT).show(); return; }
                if (disabling) {
                    if (!PasswordUtil.verify(p, prefs.getString(KEY_HASH, ""), prefs.getString(KEY_SALT, ""))) {
                        Toast.makeText(this, "Wrong password.", Toast.LENGTH_SHORT).show(); return;
                    }
                    disableGuardian();
                } else {
                    String salt = PasswordUtil.randomSalt();
                    prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, PasswordUtil.hash(p, salt)).apply();
                    enableGuardian();
                }
            })
            .setNegativeButton("CANCEL", null).show();
    }

    private void disableGuardian() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        stopService(new Intent(this, GuardianService.class));
        updateUi();
        Toast.makeText(this, "Display Guardian OFF", Toast.LENGTH_SHORT).show();
    }

    private void updateUi() {
        boolean on = prefs.getBoolean(KEY_ENABLED, false);
        status.setText(on ? "ON — display protected" : "OFF");
        toggle.setText(on ? "TURN OFF" : "TURN ON");
    }

    private boolean hasPassword() { return prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT); }
    private boolean hasOverlayPermission() { return Settings.canDrawOverlays(this); }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception e) { return false; }
    }
}
