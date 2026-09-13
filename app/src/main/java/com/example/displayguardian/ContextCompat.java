package com.example.displayguardian;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class ContextCompat {
    static void startForeground(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }
}
