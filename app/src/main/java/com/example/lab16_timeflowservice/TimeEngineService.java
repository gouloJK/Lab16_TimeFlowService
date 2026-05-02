package com.example.lab16_timeflowservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TimeEngineService extends Service {

    // Bridge for Activity-Service communication
    private final IBinder serviceBridge = new TimerBinder();

    // Timer variables
    private int elapsedSeconds = 0;
    private boolean engineActive = false;
    private ScheduledExecutorService schedulerEngine;
    private ScheduledFuture<?> timerTask;

    // Callback for live UI updates
    private TimerUpdateCallback updateCallback;

    // Notification constants
    private static final int PERSISTENT_NOTIFY_ID = 2001;
    private static final String CHANNEL_ID = "timer_flow_channel";
    private NotificationManager systemNotificationManager;

    // Interface for Activity callback
    public interface TimerUpdateCallback {
        void onTimerUpdate(int seconds);
    }

    // Inner class to bridge Activity and Service
    public class TimerBinder extends Binder {
        public TimeEngineService getServiceInstance() {
            return TimeEngineService.this;
        }
    }

    // Set callback from Activity
    public void setUpdateCallback(TimerUpdateCallback callback) {
        this.updateCallback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        systemNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        initializeNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String actionCommand = (intent != null) ? intent.getAction() : null;

        // Handle stop request
        if ("TERMINATE_TIMER".equals(actionCommand)) {
            performCleanShutdown();
            return START_NOT_STICKY;
        }

        // Start timer if not already running
        if (!engineActive) {
            engineActive = true;
            startForeground(PERSISTENT_NOTIFY_ID, buildTimerNotification());
            activateTimerEngine();
        }

        return START_STICKY;
    }

    // Starts the timer engine
    private void activateTimerEngine() {
        schedulerEngine = Executors.newSingleThreadScheduledExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        timerTask = schedulerEngine.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        elapsedSeconds++;
                        refreshLiveNotification();

                        // Notify Activity on main thread
                        mainHandler.post(() -> {
                            if (updateCallback != null) {
                                updateCallback.onTimerUpdate(elapsedSeconds);
                            }
                        });
                    }
                },
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    // Creates notification channel (required for Android 8+)
    private void initializeNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel timerChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Timer Flow Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            timerChannel.setDescription("Shows active timer status");
            systemNotificationManager.createNotificationChannel(timerChannel);
        }
    }

    // Builds the persistent notification
    private Notification buildTimerNotification() {
        Intent launchIntent = new Intent(this, TimerDashboardActivity.class);
        PendingIntent pendingLaunch = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏱ Timer Active")
                .setContentText("Elapsed: " + convertToTimerFormat(elapsedSeconds))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingLaunch)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(getColor(android.R.color.holo_blue_dark))
                .build();
    }

    // Updates notification with new time
    private void refreshLiveNotification() {
        systemNotificationManager.notify(PERSISTENT_NOTIFY_ID, buildTimerNotification());
    }

    // Converts seconds to MM:SS format
    private String convertToTimerFormat(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    // Public methods for Activity to access
    public int fetchCurrentTime() {
        return elapsedSeconds;
    }

    public boolean isEngineOperational() {
        return engineActive;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBridge;
    }

    // Clean shutdown method
    private void performCleanShutdown() {
        engineActive = false;
        if (updateCallback != null) {
            updateCallback = null;
        }
        if (timerTask != null) {
            timerTask.cancel(true);
        }
        if (schedulerEngine != null) {
            schedulerEngine.shutdownNow();
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        engineActive = false;
        if (updateCallback != null) {
            updateCallback = null;
        }
        if (timerTask != null) {
            timerTask.cancel(true);
        }
        if (schedulerEngine != null) {
            schedulerEngine.shutdownNow();
        }
        stopForeground(true);
        super.onDestroy();
    }
}