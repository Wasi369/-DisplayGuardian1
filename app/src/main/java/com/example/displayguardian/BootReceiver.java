package com.example.displayguardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (context.getSharedPreferences("guardian", Context.MODE_PRIVATE).getBoolean("enabled", false)) {
                ContextCompat.startForeground(context, new Intent(context, GuardianService.class));
            }
        }
    }
}
