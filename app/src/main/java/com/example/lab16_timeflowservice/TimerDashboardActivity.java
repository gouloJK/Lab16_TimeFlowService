package com.example.lab16_timeflowservice;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class TimerDashboardActivity extends AppCompatActivity implements TimeEngineService.TimerUpdateCallback {

    // UI elements
    private TextView timeDisplayLabel;
    private Button activateServiceButton;
    private Button terminateServiceButton;

    // Service connection
    private TimeEngineService timerServiceEngine;
    private boolean serviceConnected = false;

    private static final int NOTIFICATION_PERMISSION_CODE = 100;

    // Service connection implementation
    private final ServiceConnection serviceLink = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimeEngineService.TimerBinder bridge = (TimeEngineService.TimerBinder) service;
            timerServiceEngine = bridge.getServiceInstance();
            timerServiceEngine.setUpdateCallback(TimerDashboardActivity.this);
            serviceConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnected = false;
            if (timerServiceEngine != null) {
                timerServiceEngine.setUpdateCallback(null);
            }
            timerServiceEngine = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard_layout);

        // Initialize UI components
        timeDisplayLabel = findViewById(R.id.timeDisplayLabel);
        activateServiceButton = findViewById(R.id.activateServiceButton);
        terminateServiceButton = findViewById(R.id.terminateServiceButton);

        // Button listeners
        activateServiceButton.setOnClickListener(v -> initiateTimerService());
        terminateServiceButton.setOnClickListener(v -> haltTimerService());

        // Check notification permission
        requestNotificationPermissionIfNeeded();
    }

    // Callback from service with updated time
    @Override
    public void onTimerUpdate(int seconds) {
        runOnUiThread(() -> timeDisplayLabel.setText(formatTimeForDisplay(seconds)));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE
                );
            }
        }
    }

    private void initiateTimerService() {
        Intent serviceIntent = new Intent(this, TimeEngineService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindService(serviceIntent, serviceLink, Context.BIND_AUTO_CREATE);

        activateServiceButton.setEnabled(false);
        terminateServiceButton.setEnabled(true);
    }

    private void haltTimerService() {
        // Store final time before stopping
        if (serviceConnected && timerServiceEngine != null) {
            int finalTime = timerServiceEngine.fetchCurrentTime();
            timeDisplayLabel.setText(formatTimeForDisplay(finalTime));
        }

        // Clear callback to stop updates
        if (timerServiceEngine != null) {
            timerServiceEngine.setUpdateCallback(null);
        }

        Intent serviceIntent = new Intent(this, TimeEngineService.class);
        serviceIntent.setAction("TERMINATE_TIMER");
        startService(serviceIntent);

        if (serviceConnected) {
            unbindService(serviceLink);
            serviceConnected = false;
        }

        // Time stays displayed until you start again
        activateServiceButton.setEnabled(true);
        terminateServiceButton.setEnabled(false);
    }

    private String formatTimeForDisplay(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) {
            if (timerServiceEngine != null) {
                timerServiceEngine.setUpdateCallback(null);
            }
            unbindService(serviceLink);
            serviceConnected = false;
        }
    }
}