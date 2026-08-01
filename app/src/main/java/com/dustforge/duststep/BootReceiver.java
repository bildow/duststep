package com.dustforge.duststep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!StepStore.isTracking(context)) return;
        // TYPE_STEP_COUNTER is scoped to the current boot. Flag the first
        // reading so StepStore preserves today's accumulated total even if the
        // new counter is numerically above the original daily baseline.
        StepStore.markCounterMayHaveReset(context);
        Intent service = new Intent(context, StepService.class).setAction(StepService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
