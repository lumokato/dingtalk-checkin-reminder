package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GuardDecisionEngineTest {
    @Test
    public void checkInWaitsForLocationBeforeAnyRangeDecision() {
        assertEquals(
                GuardDecisionEngine.CheckInAction.WAIT_LOCATION,
                GuardDecisionEngine.decideCheckIn(false, false, true, true)
        );
    }

    @Test
    public void checkInWaitsForFreshLocationBeforeOpeningDingTalk() {
        assertEquals(
                GuardDecisionEngine.CheckInAction.WAIT_FRESH_LOCATION,
                GuardDecisionEngine.decideCheckIn(true, true, true, true)
        );
    }

    @Test
    public void checkInOutsideTargetDoesNotOpenDingTalk() {
        assertEquals(
                GuardDecisionEngine.CheckInAction.OUTSIDE_TARGET,
                GuardDecisionEngine.decideCheckIn(true, false, false, true)
        );
    }

    @Test
    public void checkInInsideTargetWaitsUntilAutoOpenReady() {
        assertEquals(
                GuardDecisionEngine.CheckInAction.INSIDE_WAIT_UNLOCK,
                GuardDecisionEngine.decideCheckIn(true, false, true, false)
        );
    }

    @Test
    public void checkInInsideTargetOpensWhenAutoOpenReady() {
        assertEquals(
                GuardDecisionEngine.CheckInAction.INSIDE_OPEN_DINGTALK,
                GuardDecisionEngine.decideCheckIn(true, false, true, true)
        );
    }

    @Test
    public void checkOutWithoutLocationRequirementCanOpenWhenAutoOpenReady() {
        assertEquals(
                GuardDecisionEngine.CheckOutAction.OPEN_DINGTALK,
                GuardDecisionEngine.decideCheckOut(false, false, true, false, true)
        );
    }

    @Test
    public void checkOutWithoutLocationRequirementWaitsUntilAutoOpenReady() {
        assertEquals(
                GuardDecisionEngine.CheckOutAction.WAIT_UNLOCK,
                GuardDecisionEngine.decideCheckOut(false, false, true, false, false)
        );
    }

    @Test
    public void checkOutWithLocationRequirementRejectsStaleLocation() {
        assertEquals(
                GuardDecisionEngine.CheckOutAction.WAIT_FRESH_LOCATION,
                GuardDecisionEngine.decideCheckOut(true, true, true, true, true)
        );
    }

    @Test
    public void checkOutWithLocationRequirementRejectsOutsideTarget() {
        assertEquals(
                GuardDecisionEngine.CheckOutAction.OUTSIDE_TARGET,
                GuardDecisionEngine.decideCheckOut(true, true, false, false, true)
        );
    }
}
