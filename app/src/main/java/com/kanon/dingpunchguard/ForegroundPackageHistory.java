package com.kanon.dingpunchguard;

final class ForegroundPackageHistory {
    private static final long EVENT_TIME_TOLERANCE_MILLIS = 1_000L;

    private final String targetPackage;
    private final long sinceMillis;
    private boolean targetSeen;
    private String latestPackage = "";
    private long latestMillis = Long.MIN_VALUE;

    ForegroundPackageHistory(String targetPackage, long sinceMillis) {
        this.targetPackage = targetPackage == null ? "" : targetPackage;
        this.sinceMillis = sinceMillis;
    }

    void accept(String packageName, long eventTimeMillis) {
        if (eventTimeMillis < sinceMillis - EVENT_TIME_TOLERANCE_MILLIS) {
            return;
        }
        String safePackageName = packageName == null ? "" : packageName;
        if (targetPackage.equals(safePackageName)) {
            targetSeen = true;
        }
        if (eventTimeMillis >= latestMillis) {
            latestMillis = eventTimeMillis;
            latestPackage = safePackageName;
        }
    }

    boolean targetSeen() {
        return targetSeen;
    }

    String latestPackage() {
        return latestPackage;
    }
}
