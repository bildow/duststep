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
    private static final String KEY_LAST_COUNTER = "last_counter";
    private static final String KEY_REBASE_ON_NEXT_COUNTER = "rebase_on_next_counter";
    private static final String KEY_STEPS = "steps";
    private static final String HISTORY_PREFIX = "history_";
    // Estimated walking stride for a 6'9" person: 0.415 x height, about 1,885 steps/mile.
    private static final float STEPS_PER_MILE = 1885f;

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

    static void markCounterMayHaveReset(Context context) {
        prefs(context).edit().putBoolean(KEY_REBASE_ON_NEXT_COUNTER, true).apply();
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
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return Math.round(total / (float) days);
    }

    static String estimatedMiles(int steps) {
        return String.format(Locale.US, "%.1f", steps / STEPS_PER_MILE);
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
        float baseline = p.getFloat(KEY_BASELINE, -1f);
        int recordedSteps = p.getInt(KEY_STEPS, 0);
        float lastCounter = p.getFloat(KEY_LAST_COUNTER, -1f);
        // The counter normally equals baseline plus today's recorded steps. Prefer
        // the actual last reading when it is available so a manual reset cannot
        // replay an old hardware total into the new day.
        float nextBaseline = lastCounter >= 0f ? lastCounter : baseline + recordedSteps;
        p.edit()
            .putString(KEY_DAY, today)
            .putFloat(KEY_BASELINE, nextBaseline)
            .putFloat(KEY_LAST_COUNTER, nextBaseline)
            .putInt(KEY_STEPS, 0)
            .putInt(HISTORY_PREFIX + today, 0)
            .apply();
    }

    static void setStepsToday(Context context, int steps) {
        ensureToday(context);
        SharedPreferences p = prefs(context);
        String today = today();
        int total = Math.max(0, steps);
        float baseline = p.getFloat(KEY_BASELINE, -1f);
        float lastCounter = p.getFloat(KEY_LAST_COUNTER, -1f);
        if (lastCounter >= 0f) {
            baseline = lastCounter - total;
        }
        p.edit()
            .putFloat(KEY_BASELINE, baseline)
            .putInt(KEY_STEPS, total)
            .putInt(HISTORY_PREFIX + today, total)
            .apply();
    }

    static int applyStepCounter(Context context, float cumulativeSteps) {
        SharedPreferences p = prefs(context);
        String today = today();
        String storedDay = p.getString(KEY_DAY, "");
        float baseline = p.getFloat(KEY_BASELINE, -1f);
        if (!today.equals(storedDay)) {
            archiveStoredDay(p, storedDay);
            baseline = cumulativeSteps;
            p.edit()
                .putString(KEY_DAY, today)
                .putFloat(KEY_BASELINE, baseline)
                .putFloat(KEY_LAST_COUNTER, cumulativeSteps)
                .putInt(KEY_STEPS, 0)
                .apply();
            return 0;
        }

        int recordedSteps = p.getInt(KEY_STEPS, 0);
        float lastCounter = p.getFloat(KEY_LAST_COUNTER, -1f);
        boolean rebasePending = p.getBoolean(KEY_REBASE_ON_NEXT_COUNTER, false);
        if (baseline < 0f) {
            // The first event after a day rollover establishes a fresh baseline.
            baseline = cumulativeSteps;
            p.edit()
                .putFloat(KEY_BASELINE, baseline)
                .putFloat(KEY_LAST_COUNTER, cumulativeSteps)
                .putInt(KEY_STEPS, 0)
                .apply();
            return 0;
        }

        if (rebasePending || (lastCounter >= 0f && cumulativeSteps < lastCounter)) {
            // TYPE_STEP_COUNTER resets when Android restarts. Preserve the steps
            // already recorded for this tracking day and rebase against the last
            // observed hardware value. Comparing to the baseline is insufficient:
            // a fresh counter can be above the original baseline but still far
            // behind the pre-restart counter.
            baseline = cumulativeSteps - recordedSteps;
        }
        int calculatedSteps = Math.max(0, Math.round(cumulativeSteps - baseline));
        // Sensor callbacks must never decrease an already persisted daily total.
        int steps = Math.max(recordedSteps, calculatedSteps);
        p.edit()
            .putFloat(KEY_BASELINE, baseline)
            .putFloat(KEY_LAST_COUNTER, cumulativeSteps)
            .putBoolean(KEY_REBASE_ON_NEXT_COUNTER, false)
            .putInt(KEY_STEPS, steps)
            .putInt(HISTORY_PREFIX + today, steps)
            .apply();
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
            p.edit()
                .putString(KEY_DAY, today)
                .putFloat(KEY_BASELINE, -1f)
                .putFloat(KEY_LAST_COUNTER, -1f)
                .putBoolean(KEY_REBASE_ON_NEXT_COUNTER, false)
                .putInt(KEY_STEPS, 0)
                .apply();
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
