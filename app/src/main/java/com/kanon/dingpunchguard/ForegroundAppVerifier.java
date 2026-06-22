package com.kanon.dingpunchguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

final class ForegroundAppVerifier {
    private ForegroundAppVerifier() {
    }

    static boolean hasUsageStatsAccess(Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }
        try {
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.getPackageName()
                );
            } else {
                mode = appOpsManager.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.getPackageName()
                );
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            AppLog.e(context, "usage stats access check failed", e);
            return false;
        }
    }

    static boolean wasPackageForegroundSince(Context context, String packageName, long sinceMillis) {
        return foregroundPackageHistory(context, packageName, sinceMillis).targetSeen();
    }

    static String lastForegroundPackageSince(Context context, long sinceMillis) {
        return foregroundPackageHistory(context, "", sinceMillis).latestPackage();
    }

    private static ForegroundPackageHistory foregroundPackageHistory(Context context, String targetPackage, long sinceMillis) {
        ForegroundPackageHistory history = new ForegroundPackageHistory(targetPackage, sinceMillis);
        if (!hasUsageStatsAccess(context)) {
            return history;
        }
        UsageStatsManager usageStatsManager = context.getSystemService(UsageStatsManager.class);
        if (usageStatsManager == null) {
            return history;
        }
        long start = Math.max(0L, sinceMillis - 1_000L);
        long end = System.currentTimeMillis() + 1_000L;
        try {
            UsageEvents events = usageStatsManager.queryEvents(start, end);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type != UsageEvents.Event.MOVE_TO_FOREGROUND && !isActivityResumed(type)) {
                    continue;
                }
                if (event.getTimeStamp() < sinceMillis - 1_000L) {
                    continue;
                }
                history.accept(event.getPackageName(), event.getTimeStamp());
            }
        } catch (Exception e) {
            AppLog.e(context, "foreground package verification failed", e);
        }
        return history;
    }

    private static boolean isActivityResumed(int eventType) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && eventType == UsageEvents.Event.ACTIVITY_RESUMED;
    }
}
