package com.kanon.dingpunchguard;

final class LocationFreshness {
    private LocationFreshness() {
    }

    static boolean isStale(boolean hasLocation, long ageMillis, long staleAfterMillis) {
        return !hasLocation || ageMillis > staleAfterMillis;
    }
}
