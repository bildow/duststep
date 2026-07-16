package com.dustforge.duststep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

public class StepService extends Service implements SensorEventListener {
    static final String ACTION_START = "com.dustforge.duststep.START";
    static final String ACTION_STOP = "com.dustforge.duststep.STOP";
    static final String ACTION_UPDATE = "com.dustforge.duststep.UPDATE";
    private static final String CHANNEL_ID = "duststep_tracking";
    private static final int NOTIFICATION_ID = 42;

    private SensorManager sensorManager;
    private Sensor activeSensor;
    private boolean usingDetector;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            stopSelf();
            return START_NOT_STICKY;
        }
        StepStore.setTracking(this, true);
        startForeground(NOTIFICATION_ID, notification(StepStore.stepsToday(this)));
        startSensor();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSensor();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int steps;
        if (usingDetector) {
            steps = StepStore.applyStepDetector(this);
        } else {
            steps = StepStore.applyStepCounter(this, event.values[0]);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification(steps));
        // Keep dynamic activity receivers inside this app; the visible activity also polls while resumed.
        sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("steps", steps));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startSensor() {
        stopSensor();
        activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        usingDetector = false;
        if (activeSensor == null) {
            activeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            usingDetector = true;
        }
        if (activeSensor != null) {
            sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopTracking() {
        StepStore.setTracking(this, false);
        stopSensor();
        stopForeground(true);
    }

    private void stopSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private Notification notification(int steps) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return b.setContentTitle("DustStep")
            .setContentText(steps + " steps today")
            .setSmallIcon(com.dustforge.duststep.R.drawable.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Step tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps local step tracking active.");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }
}
