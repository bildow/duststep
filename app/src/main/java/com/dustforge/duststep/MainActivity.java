package com.dustforge.duststep;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 15, 18);
    private static final int PANEL = Color.rgb(24, 28, 32);
    private static final int PANEL_ALT = Color.rgb(13, 33, 43);
    private static final int BORDER = Color.rgb(49, 80, 96);
    private static final int TEXT = Color.rgb(232, 238, 244);
    private static final int DIM = Color.rgb(152, 166, 178);
    private static final int ACCENT = Color.rgb(93, 220, 255);
    private static final int GREEN = Color.rgb(105, 199, 177);

    private TextView steps;
    private TextView weekly;
    private TextView status;
    private Button toggle;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, new IntentFilter(StepService.ACTION_UPDATE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, new IntentFilter(StepService.ACTION_UPDATE));
        }
        refresh();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(receiver);
        super.onPause();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setBackgroundColor(BG);

        TextView title = text("DustStep", 34, ACCENT, true);
        TextView subtitle = text("No ads. No login. No internet.", 15, DIM, false);
        subtitle.setPadding(0, dp(6), 0, dp(28));

        steps = text("0", 76, TEXT, true);
        TextView label = text("steps today", 16, DIM, false);
        label.setPadding(0, 0, 0, dp(10));

        weekly = text("7-day average: 0", 17, GREEN, true);
        weekly.setPadding(0, 0, 0, dp(18));

        status = text("", 14, DIM, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(24));

        toggle = button("Start tracking");
        toggle.setOnClickListener(v -> {
            if (StepStore.isTracking(this)) stopTracking();
            else startTracking();
        });

        Button reset = button("Reset today");
        reset.setOnClickListener(v -> {
            StepStore.resetToday(this);
            refresh();
        });

        root.addView(title);
        root.addView(subtitle);
        root.addView(steps);
        root.addView(label);
        root.addView(weekly);
        root.addView(status);
        root.addView(toggle);
        root.addView(reset);
        setContentView(root);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setBackground(buttonBackground(false));
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable buttonBackground(boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(active ? PANEL_ALT : PANEL);
        d.setStroke(dp(1), active ? ACCENT : BORDER);
        d.setCornerRadius(dp(12));
        return d;
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 10);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private void startTracking() {
        requestNeededPermissions();
        Intent i = new Intent(this, StepService.class).setAction(StepService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        StepStore.setTracking(this, true);
        refresh();
    }

    private void stopTracking() {
        Intent i = new Intent(this, StepService.class).setAction(StepService.ACTION_STOP);
        startService(i);
        StepStore.setTracking(this, false);
        refresh();
    }

    private void refresh() {
        steps.setText(String.valueOf(StepStore.stepsToday(this)));
        weekly.setText("7-day average: " + StepStore.weeklyAverage(this));
        boolean tracking = StepStore.isTracking(this);
        toggle.setText(tracking ? "Stop tracking" : "Start tracking");
        toggle.setBackground(buttonBackground(tracking));
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        Sensor detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        String sensor = counter != null ? "hardware step counter" : detector != null ? "step detector fallback" : "no step sensor found";
        String mode = tracking ? "Tracking is on" : "Tracking is off";
        status.setText(mode + "\nSensor: " + sensor + "\n" + StepStore.resetNote());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
