package com.kanon.dingpunchguard;

final class CheckInPollingPolicy {
    static final long TICK_VERY_NEAR_MILLIS = 5_000L;
    static final long TICK_APPROACHING_MILLIS = 15_000L;
    static final long TICK_DEFAULT_MILLIS = 15_000L;
    static final long TICK_FAR_MILLIS = 60_000L;
    static final long LOCATION_STALE_MILLIS = 120_000L;
    static final long LOCATION_FAST_MILLIS = 5_000L;
    static final long LOCATION_NEAR_MILLIS = 15_000L;
    static final long LOCATION_WATCH_MILLIS = 60_000L;
    static final long LOCATION_FAR_MILLIS = 60_000L;
    static final float LOCATION_FAST_METERS = 0.0f;
    static final float LOCATION_NEAR_METERS = 5.0f;
    static final float LOCATION_WATCH_METERS = 30.0f;
    static final float LOCATION_FAR_METERS = 30.0f;
    static final float EDGE_EXTRA_METERS = 50.0f;
    static final float APPROACH_EXTRA_METERS = 500.0f;
    static final float APPROACH_DELTA_METERS = 5.0f;

    private CheckInPollingPolicy() {
    }

    static long nextTickDelayMillis(
            boolean checkInPhase,
            boolean checkOutPhase,
            boolean checkoutRequiresLocation,
            boolean checkInInsideLatched,
            boolean unlockBurst,
            boolean hasLocation,
            boolean insideTarget,
            float distanceMeters,
            int radiusMeters,
            boolean approachActive
    ) {
        if (checkInPhase && checkInInsideLatched) {
            return TICK_VERY_NEAR_MILLIS;
        }
        if (unlockBurst) {
            return TICK_VERY_NEAR_MILLIS;
        }
        if (!checkInPhase && !checkoutRequiresLocation) {
            return checkOutPhase ? TICK_FAR_MILLIS : TICK_DEFAULT_MILLIS;
        }
        if (!hasLocation) {
            return checkInPhase ? TICK_VERY_NEAR_MILLIS : TICK_APPROACHING_MILLIS;
        }

        float extraMeters = extraMeters(distanceMeters, radiusMeters);
        if (insideTarget || extraMeters <= EDGE_EXTRA_METERS) {
            return TICK_VERY_NEAR_MILLIS;
        }
        if (checkInPhase && extraMeters <= APPROACH_EXTRA_METERS) {
            return TICK_VERY_NEAR_MILLIS;
        }
        if (approachActive) {
            return TICK_APPROACHING_MILLIS;
        }
        return TICK_FAR_MILLIS;
    }

    static LocationRequest locationRequest(
            boolean checkInInsideLatched,
            boolean forceFast,
            boolean unlockBurst,
            boolean hasLocation,
            boolean insideTarget,
            float distanceMeters,
            int radiusMeters,
            boolean approachActive
    ) {
        if (checkInInsideLatched) {
            return new LocationRequest(LOCATION_WATCH_MILLIS, LOCATION_WATCH_METERS);
        }
        if (forceFast || unlockBurst) {
            return new LocationRequest(LOCATION_FAST_MILLIS, LOCATION_FAST_METERS);
        }
        if (!hasLocation) {
            return new LocationRequest(LOCATION_NEAR_MILLIS, LOCATION_NEAR_METERS);
        }

        float extraMeters = extraMeters(distanceMeters, radiusMeters);
        if (insideTarget || extraMeters <= EDGE_EXTRA_METERS) {
            return new LocationRequest(LOCATION_FAST_MILLIS, LOCATION_FAST_METERS);
        }
        if (approachActive) {
            return new LocationRequest(LOCATION_NEAR_MILLIS, LOCATION_NEAR_METERS);
        }
        if (extraMeters <= APPROACH_EXTRA_METERS) {
            return new LocationRequest(LOCATION_WATCH_MILLIS, LOCATION_WATCH_METERS);
        }
        return new LocationRequest(LOCATION_FAR_MILLIS, LOCATION_FAR_METERS);
    }

    static boolean approachActive(
            boolean checkInPhase,
            boolean alreadyLatched,
            boolean hasLocation,
            boolean insideTarget,
            float distanceMeters,
            int radiusMeters,
            Float previousDistanceMeters
    ) {
        if (!checkInPhase) {
            return false;
        }
        if (alreadyLatched) {
            return true;
        }
        if (!hasLocation || insideTarget) {
            return false;
        }
        float extraMeters = extraMeters(distanceMeters, radiusMeters);
        if (extraMeters <= EDGE_EXTRA_METERS || extraMeters > APPROACH_EXTRA_METERS) {
            return false;
        }
        return previousDistanceMeters != null
                && previousDistanceMeters - distanceMeters > APPROACH_DELTA_METERS;
    }

    private static float extraMeters(float distanceMeters, int radiusMeters) {
        return Math.max(0.0f, distanceMeters - Math.max(1, radiusMeters));
    }

    static final class LocationRequest {
        final long intervalMillis;
        final float minDistanceMeters;

        LocationRequest(long intervalMillis, float minDistanceMeters) {
            this.intervalMillis = intervalMillis;
            this.minDistanceMeters = minDistanceMeters;
        }
    }
}
