package com.harlequinxd.drivehard;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/**
 * How hot the phone is, as a level 0 (cool) to 3 (hot), from the system's thermal status
 * (Android 10+) and thermal headroom forecast (Android 11+). Older phones always report 0.
 */
final class Thermal {
    private final PowerManager pm;
    private volatile int status;
    private int level;
    private long nextPoll;

    Thermal(Context context) {
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && Build.VERSION.SDK_INT >= 29) {
            try {
                status = pm.getCurrentThermalStatus();
                pm.addThermalStatusListener(s -> status = s);
            } catch (Throwable ignored) { }
        }
    }

    /** Current level; the headroom forecast is polled at most every 2 s (it is rate-limited). */
    int level() {
        long now = System.nanoTime();
        if (now < nextPoll) return level;
        nextPoll = now + 2_000_000_000L;
        int l = 0;
        int s = status;
        if (Build.VERSION.SDK_INT >= 29) {
            if (s >= PowerManager.THERMAL_STATUS_SEVERE) l = 3;
            else if (s >= PowerManager.THERMAL_STATUS_MODERATE) l = 2;
            else if (s >= PowerManager.THERMAL_STATUS_LIGHT) l = 1;
        }
        if (pm != null && Build.VERSION.SDK_INT >= 30) {
            try {
                // forecast 10 s ahead: 1.0 means throttling starts
                float h = pm.getThermalHeadroom(10);
                if (!Float.isNaN(h)) {
                    if (h >= 0.95f) l = Math.max(l, 3);
                    else if (h >= 0.85f) l = Math.max(l, 2);
                    else if (h >= 0.7f) l = Math.max(l, 1);
                }
            } catch (Throwable ignored) { }
        }
        // cool down slowly: one level at a time, so it does not flip back and forth
        level = l >= level ? l : level - 1;
        return level;
    }
}
