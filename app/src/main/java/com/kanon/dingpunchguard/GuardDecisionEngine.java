package com.kanon.dingpunchguard;

final class GuardDecisionEngine {
    enum CheckInAction {
        WAIT_LOCATION,
        WAIT_FRESH_LOCATION,
        OUTSIDE_TARGET,
        INSIDE_WAIT_UNLOCK,
        INSIDE_OPEN_DINGTALK
    }

    enum CheckOutAction {
        WAIT_LOCATION,
        WAIT_FRESH_LOCATION,
        OUTSIDE_TARGET,
        WAIT_UNLOCK,
        OPEN_DINGTALK
    }

    private GuardDecisionEngine() {
    }

    static CheckInAction decideCheckIn(
            boolean hasLocation,
            boolean locationStale,
            boolean insideTarget,
            boolean autoOpenReady
    ) {
        if (!hasLocation) {
            return CheckInAction.WAIT_LOCATION;
        }
        if (locationStale) {
            return CheckInAction.WAIT_FRESH_LOCATION;
        }
        if (!insideTarget) {
            return CheckInAction.OUTSIDE_TARGET;
        }
        return autoOpenReady ? CheckInAction.INSIDE_OPEN_DINGTALK : CheckInAction.INSIDE_WAIT_UNLOCK;
    }

    static CheckOutAction decideCheckOut(
            boolean requiresLocation,
            boolean hasLocation,
            boolean locationStale,
            boolean insideTarget,
            boolean autoOpenReady
    ) {
        if (requiresLocation) {
            if (!hasLocation) {
                return CheckOutAction.WAIT_LOCATION;
            }
            if (locationStale) {
                return CheckOutAction.WAIT_FRESH_LOCATION;
            }
            if (!insideTarget) {
                return CheckOutAction.OUTSIDE_TARGET;
            }
        }
        return autoOpenReady ? CheckOutAction.OPEN_DINGTALK : CheckOutAction.WAIT_UNLOCK;
    }
}
