package com.kanon.dingpunchguard;

final class AppVisibility {
    private static int startedActivities;

    private AppVisibility() {
    }

    static synchronized void activityStarted() {
        startedActivities++;
    }

    static synchronized void activityStopped() {
        if (startedActivities > 0) {
            startedActivities--;
        }
    }

    static synchronized boolean isForeground() {
        return startedActivities > 0;
    }
}
