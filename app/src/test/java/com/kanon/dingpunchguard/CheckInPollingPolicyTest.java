package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CheckInPollingPolicyTest {
    private static final int RADIUS = 300;

    @Test
    public void farOutsideUsesLowFrequencyTickAndLocation() {
        assertEquals(
                CheckInPollingPolicy.TICK_FAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        true, false, 901.0f, RADIUS, false)
        );

        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 901.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAR_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAR_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void midRangeWithoutApproachPollsUnlockOftenButKeepsLocationLowFrequency() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        true, false, 650.0f, RADIUS, false)
        );

        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 650.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void midRangeAtApproachOuterBoundaryStillPollsUnlockOften() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        true, false, 800.0f, RADIUS, false)
        );
    }

    @Test
    public void beyondApproachOuterBoundaryUsesFarTickWhenNotApproaching() {
        assertEquals(
                CheckInPollingPolicy.TICK_FAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        true, false, 800.1f, RADIUS, false)
        );
    }

    @Test
    public void midRangeApproachLatchesAndUsesNearLocation() {
        assertTrue(CheckInPollingPolicy.approachActive(
                true, false, true, false, 620.0f, RADIUS, 628.0f
        ));

        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 620.0f, RADIUS, true
        );

        assertEquals(CheckInPollingPolicy.LOCATION_NEAR_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_NEAR_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void midRangeDoesNotLatchApproachWhenDistanceDropIsTooSmall() {
        assertFalse(CheckInPollingPolicy.approachActive(
                true, false, true, false, 620.0f, RADIUS, 624.9f
        ));
    }

    @Test
    public void approachDoesNotLatchOutsideCheckInPhase() {
        assertFalse(CheckInPollingPolicy.approachActive(
                false, false, true, false, 620.0f, RADIUS, 628.0f
        ));
    }

    @Test
    public void approachDoesNotLatchInsideTarget() {
        assertFalse(CheckInPollingPolicy.approachActive(
                true, false, true, true, 280.0f, RADIUS, 320.0f
        ));
    }

    @Test
    public void approachDoesNotLatchAtEdgeBecauseEdgeAlreadyUsesFastPolicy() {
        assertFalse(CheckInPollingPolicy.approachActive(
                true, false, true, false, 350.0f, RADIUS, 360.0f
        ));
    }

    @Test
    public void approachDoesNotLatchBeyondOuterBoundaryEvenWhenDistanceDrops() {
        assertFalse(CheckInPollingPolicy.approachActive(
                true, false, true, false, 801.0f, RADIUS, 900.0f
        ));
    }

    @Test
    public void approachStaysActiveAfterLatchedEvenWithoutPreviousDistance() {
        assertTrue(CheckInPollingPolicy.approachActive(
                true, true, true, false, 700.0f, RADIUS, null
        ));
    }

    @Test
    public void edgeOutsideUsesFastLocation() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 340.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void edgeOuterBoundaryUsesFastLocation() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 350.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void justOutsideEdgeUsesWatchLocationWhenNotApproaching() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 350.1f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void insideTargetPollsUnlockFrequently() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        true, true, 280.0f, RADIUS, false)
        );
    }

    @Test
    public void insideTargetUsesFastLocationBeforeInsideLatch() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, true, 280.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void insideLatchPausesContinuousFastLocationButKeepsWatchRequestIfRestarted() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                true, false, false, true, true, 280.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_WATCH_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void noLocationInCheckInWindowUsesFrequentTickButNearLocationRequest() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, false,
                        false, false, Float.MAX_VALUE, RADIUS, false)
        );

        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, false, false, Float.MAX_VALUE, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_NEAR_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_NEAR_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void unlockBurstForcesFastLocationProbe() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, true, true, false, 900.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void forceFastForcesFastLocationProbeEvenFarAway() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, true, false, true, false, 1_500.0f, RADIUS, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }

    @Test
    public void unlockBurstForcesFrequentTickEvenFarAway() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(true, false, false, false, true,
                        true, false, 1_500.0f, RADIUS, false)
        );
    }

    @Test
    public void checkoutWithoutLocationRequirementUsesSlowTick() {
        assertEquals(
                CheckInPollingPolicy.TICK_FAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(false, true, false, false, false,
                        false, false, Float.MAX_VALUE, RADIUS, false)
        );
    }

    @Test
    public void idleWithoutLocationRequirementUsesDefaultTick() {
        assertEquals(
                CheckInPollingPolicy.TICK_DEFAULT_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(false, false, false, false, false,
                        false, false, Float.MAX_VALUE, RADIUS, false)
        );
    }

    @Test
    public void checkoutWithLocationRequirementAndNoLocationUsesApproachingTick() {
        assertEquals(
                CheckInPollingPolicy.TICK_APPROACHING_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(false, true, true, false, false,
                        false, false, Float.MAX_VALUE, RADIUS, false)
        );
    }

    @Test
    public void checkoutWithLocationRequirementInsideUsesFrequentTick() {
        assertEquals(
                CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS,
                CheckInPollingPolicy.nextTickDelayMillis(false, true, true, false, false,
                        true, true, 280.0f, RADIUS, false)
        );
    }

    @Test
    public void zeroRadiusIsClampedForDistancePolicy() {
        CheckInPollingPolicy.LocationRequest request = CheckInPollingPolicy.locationRequest(
                false, false, false, true, false, 40.0f, 0, false
        );

        assertEquals(CheckInPollingPolicy.LOCATION_FAST_MILLIS, request.intervalMillis);
        assertEquals(CheckInPollingPolicy.LOCATION_FAST_METERS, request.minDistanceMeters, 0.01f);
    }
}
