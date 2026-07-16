package com.dustforge.duststep;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

final class StepStore {
    private static final String PREF = "duststep";
    private static final String KEY_TRACKING = "tracking";
    private static final String KEY_DAY = "day";
    private static final String KEY_BASELINE = "baseline";
    private static final String KEY_STEPS = "steps";
    private static final String HISTORY_PREFIX = "history_";

    private StepStore() {}

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String today() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -4);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    static String resetNote() {
        return "Daily reset: 4:00 AM";
    }

    static boolean isTracking(Context context) {
        return prefs(context).getBoolean(KEY_TRACKING, false);
    }

    static void setTracking(Context context, boolean tracking) {
        prefs(context).edit().putBoolean(KEY_TRACKING, tracking).apply();
    }

    static int stepsToday(Context context) {
        ensureToday(context);
        return prefs(context).getInt(KEY_STEPS, 0);
    }

    static int weeklyAverage(Context context) {
        ensureToday(context);
        SharedPreferences p = prefs(context);
        Calendar cal = trackingCalendar();
        int days = weekDaysSoFar();
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1));
        int total = 0;
        for (int i = 0; i < days; i++) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
            total += day.equals(today()) ? p.getInt(KEY_STEPS, 0) : p.getInt(HISTORY_PREFIX + day, 0);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return Math.round(total / (float) days);
    }

    static int weekDaysSoFar() {
        int dayOfWeek = trackingCalendar().get(Calendar.DAY_OF_WEEK);
        // Calendar.MONDAY is 2. Monday is day one; Sunday is day seven.
        return ((dayOfWeek + 5) % 7) + 1;
    }

    static String recentHistory(Context context) {
        ensureToday(context);
        SharedPreferences p = prefs(context);
        Calendar cal = trackingCalendar();
        StringBuilder out = new StringBuilder("Recent days");
        SimpleDateFormat label = new SimpleDateFormat("EEE, MMM d", Locale.US);
        for (int i = 0; i < 7; i++) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
            int count = day.equals(today()) ? p.getInt(KEY_STEPS, 0) : p.getInt(HISTORY_PREFIX + day, 0);
            out.append("\n").append(label.format(cal.getTime())).append(": ").append(count);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return out.toString();
    }

    static void resetToday(Context context) {
        SharedPreferences p = prefs(context);
        String today = today();
        p.edit()
            .putString(KEY_DAY, today)
            .putFloat(KEY_BASELINE, p.getFloat(KEY_BASELINE, -1f) + p.getInt(KEY_STEPS, 0))
            .putInt(KEY_STEPS, 0)
            .putInt(HISTORY_PREFIX + today, 0)
            .apply();
    }

    static int applyStepCounter(Context context, float cumulativeSteps) {
        SharedPreferences p = prefs(context);
        String today = today();
        String storedDay = p.getString(KEY_DAY, "");
        float baseline = p.getFloat(KEY_BASELINE, -1f);
        if (!today.equals(storedDay) || baseline < 0f || cumulativeSteps < baseline) {
            archiveStoredDay(p, storedDay);
            baseline = cumulativeSteps;
            p.edit().putString(KEY_DAY, today).putFloat(KEY_BASELINE, baseline).putInt(KEY_STEPS, 0).apply();
            return 0;
        }
        int steps = Math.max(0, Math.round(cumulativeSteps - baseline));
        p.edit().putInt(KEY_STEPS, steps).putInt(HISTORY_PREFIX + today, steps).apply();
        return steps;
    }

    static int applyStepDetector(Context context) {
        ensureToday(context);
        SharedPreferences p = prefs(context);
        String today = today();
        int steps = p.getInt(KEY_STEPS, 0) + 1;
        p.edit().putInt(KEY_STEPS, steps).putInt(HISTORY_PREFIX + today, steps).apply();
        return steps;
    }

    private static void ensureToday(Context context) {
        SharedPreferences p = prefs(context);
        String today = today();
        String storedDay = p.getString(KEY_DAY, "");
        if (!today.equals(storedDay)) {
            archiveStoredDay(p, storedDay);
            p.edit().putString(KEY_DAY, today).putFloat(KEY_BASELINE, -1f).putInt(KEY_STEPS, 0).apply();
        }
    }

    private static void archiveStoredDay(SharedPreferences p, String storedDay) {
        if (storedDay == null || storedDay.isEmpty()) return;
        p.edit().putInt(HISTORY_PREFIX + storedDay, p.getInt(KEY_STEPS, 0)).apply();
    }

    private static Calendar trackingCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -4);
        return cal;
    }
}
