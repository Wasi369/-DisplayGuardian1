package com.example.displayguardian;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GuardianService extends Service {
    public static GuardianService INSTANCE;
    public static final long IDLE_MS = 15_000L;
    private static final int NOTIFICATION_ID = 701;
    private PowerManager.WakeLock wakeLock;
    private WindowManager wm;
    private View lockView;
    private Handler handler;
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Runnable idleRunnable = this::showLock;
    private BroadcastReceiver screenReceiver;

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        handler = new Handler(Looper.getMainLooper());
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        registerScreenReceiver();
        resetIdleTimer();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        acquireWakeLock();
        resetIdleTimer();
        return START_STICKY;
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        if (wakeLock == null) {
            int level = PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
            wakeLock = pm.newWakeLock(level, "DisplayGuardian:KeepScreenOn");
            wakeLock.setReferenceCounted(false);
        }
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    try {
                        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
                        PowerManager.WakeLock w = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "DisplayGuardian:WakeAfterPower");
                        w.acquire(3_000L);
                    } catch (Exception ignored) {}
                    resetIdleTimer();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    acquireWakeLock();
                    resetIdleTimer();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);
    }

    public static void userActivity() {
        if (INSTANCE != null) INSTANCE.resetIdleTimer();
    }

    private void resetIdleTimer() {
        if (locked.get()) return;
        handler.removeCallbacks(idleRunnable);
        handler.postDelayed(idleRunnable, IDLE_MS);
    }

    private void showLock() {
        if (locked.get() || !getSharedPreferences("guardian", MODE_PRIVATE).getBoolean("enabled", false)) return;
        if (!Settings.canDrawOverlays(this)) return;
        locked.set(true);
        final View view = getLayoutInflater().inflate(R.layout.lock_overlay, null);
        lockView = view;
        EditText password = view.findViewById(R.id.password);
        Button unlock = view.findViewById(R.id.unlock);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 21) {
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
        lp.screenBrightness = 0.05f;
        lp.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(view, lp); } catch (Exception e) { locked.set(false); return; }

        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        unlock.setOnClickListener(v -> {
            String entered = password.getText().toString();
            android.content.SharedPreferences p = getSharedPreferences("guardian", MODE_PRIVATE);
            if (PasswordUtil.verify(entered, p.getString("password_hash", ""), p.getString("password_salt", ""))) {
                hideLock();
            } else {
                password.setError("Wrong password");
                password.setText("");
            }
        });
        password.setOnEditorActionListener((v, actionId, event) -> { unlock.performClick(); return true; });
        password.requestFocus();
        handler.postDelayed(() -> {
            try { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(password, InputMethodManager.SHOW_IMPLICIT); } catch (Exception ignored) {}
        }, 250);
    }

    private void hideLock() {
        if (lockView != null) {
            try { wm.removeView(lockView); } catch (Exception ignored) {}
            lockView = null;
        }
        locked.set(false);
        acquireWakeLock();
        resetIdleTimer();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) { try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {} }
        if (lockView != null) { try { wm.removeView(lockView); } catch (Exception ignored) {} lockView = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        INSTANCE = null;
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("guardian", "Display Guardian", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps Display Guardian running while protection is ON.");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "guardian") : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("Display Guardian is ON").setContentText("Display will stay awake and dim after 15 seconds idle.").setOngoing(true).setContentIntent(pi).build();
    }
}
