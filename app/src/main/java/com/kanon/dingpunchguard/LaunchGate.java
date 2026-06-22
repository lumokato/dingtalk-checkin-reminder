package com.kanon.dingpunchguard;

final class LaunchGate {
    static final long USER_PRESENT_VALID_MILLIS = 60_000L;

    private LaunchGate() {
    }

    static boolean canAutoLaunch(
            boolean interactive,
            boolean keyguardLocked,
            long lastUserPresentMillis,
            long nowMillis
    ) {
        return interactive && !keyguardLocked;
    }

    static long userPresentAgeMillis(long lastUserPresentMillis, long nowMillis) {
        if (lastUserPresentMillis <= 0L) {
            return -1L;
        }
        return Math.max(0L, nowMillis - lastUserPresentMillis);
    }
}
