package com.kanon.dingpunchguard;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CheckInScenarioMatrixTest {
    private static final long NOW = 120_000L;

    @Parameterized.Parameters(name = "{index}: location={0}, device={1}, foreground={2}, trigger={3}")
    public static Iterable<Object[]> data() {
        List<Object[]> cases = new ArrayList<>();
        for (LocationCase location : LocationCase.values()) {
            for (DeviceCase device : DeviceCase.values()) {
                for (ForegroundCase foreground : ForegroundCase.values()) {
                    for (TriggerCase trigger : TriggerCase.values()) {
                        cases.add(new Object[]{location, device, foreground, trigger});
                    }
                }
            }
        }
        return cases;
    }

    private final LocationCase location;
    private final DeviceCase device;
    private final ForegroundCase foreground;
    private final TriggerCase trigger;

    public CheckInScenarioMatrixTest(
            LocationCase location,
            DeviceCase device,
            ForegroundCase foreground,
            TriggerCase trigger
    ) {
        this.location = location;
        this.device = device;
        this.foreground = foreground;
        this.trigger = trigger;
    }

    @Test
    public void checkInEventProducesExpectedFinalOutcome() {
        boolean autoOpenReady = LaunchGate.canAutoLaunch(
                device.interactive,
                device.keyguardLocked,
                device.lastUserPresentMillis,
                NOW
        );
        FinalOutcome expected = expectedOutcome();
        GuardDecisionEngine.CheckInAction action = GuardDecisionEngine.decideCheckIn(
                location.hasLocation,
                location.stale,
                location.insideTarget,
                autoOpenReady
        );

        assertEquals(scenarioText(), expected.action, action);
        assertEquals(scenarioText(), expected.shouldAttemptOpenDingTalk, action == GuardDecisionEngine.CheckInAction.INSIDE_OPEN_DINGTALK);
        assertEquals(scenarioText(), expected.shouldWaitForFreshLocation, action == GuardDecisionEngine.CheckInAction.WAIT_FRESH_LOCATION);
        assertEquals(scenarioText(), expected.shouldWaitForUnlock, action == GuardDecisionEngine.CheckInAction.INSIDE_WAIT_UNLOCK);
        assertEquals(scenarioText(), expected.shouldRecordPunchNow, false);
    }

    private FinalOutcome expectedOutcome() {
        if (!location.hasLocation) {
            return FinalOutcome.waitLocation();
        }
        if (location.stale) {
            return FinalOutcome.waitFreshLocation();
        }
        if (!location.insideTarget) {
            return FinalOutcome.outsideTarget();
        }
        return device.unlocked()
                ? FinalOutcome.openDingTalk()
                : FinalOutcome.waitUnlock();
    }

    private String scenarioText() {
        return "check-in scenario location=" + location
                + " device=" + device
                + " foreground=" + foreground
                + " trigger=" + trigger;
    }

    enum LocationCase {
        NO_LOCATION(false, false, false),
        STALE_INSIDE_299_9M(true, true, true),
        STALE_EDGE_300_1M(true, true, false),
        INSIDE_299_9M(true, false, true),
        EDGE_OUTSIDE_300_1M(true, false, false),
        EDGE_OUTSIDE_350_0M(true, false, false),
        MID_350_1M(true, false, false),
        MID_650_0M(true, false, false),
        APPROACH_BOUNDARY_800_0M(true, false, false),
        FAR_800_1M(true, false, false),
        FAR_901_0M(true, false, false);

        final boolean hasLocation;
        final boolean stale;
        final boolean insideTarget;

        LocationCase(boolean hasLocation, boolean stale, boolean insideTarget) {
            this.hasLocation = hasLocation;
            this.stale = stale;
            this.insideTarget = insideTarget;
        }
    }

    enum DeviceCase {
        SCREEN_OFF_LOCKED(false, true, 0L),
        SCREEN_ON_LOCKED(true, true, 0L),
        UNLOCKED_WITHOUT_USER_PRESENT(true, false, 0L),
        UNLOCKED_WITH_RECENT_USER_PRESENT(true, false, NOW - 1_000L),
        UNLOCKED_WITH_STALE_USER_PRESENT(true, false, NOW - LaunchGate.USER_PRESENT_VALID_MILLIS - 1L);

        final boolean interactive;
        final boolean keyguardLocked;
        final long lastUserPresentMillis;

        DeviceCase(boolean interactive, boolean keyguardLocked, long lastUserPresentMillis) {
            this.interactive = interactive;
            this.keyguardLocked = keyguardLocked;
            this.lastUserPresentMillis = lastUserPresentMillis;
        }

        boolean unlocked() {
            return interactive && !keyguardLocked;
        }
    }

    enum ForegroundCase {
        DESKTOP,
        OTHER_APP,
        DINGTALK,
        THIS_APP
    }

    enum TriggerCase {
        SCHEDULED_TICK,
        LOCATION_UPDATE,
        SCREEN_ON,
        USER_PRESENT
    }

    static final class FinalOutcome {
        final GuardDecisionEngine.CheckInAction action;
        final boolean shouldAttemptOpenDingTalk;
        final boolean shouldWaitForFreshLocation;
        final boolean shouldWaitForUnlock;
        final boolean shouldRecordPunchNow;

        private FinalOutcome(
                GuardDecisionEngine.CheckInAction action,
                boolean shouldAttemptOpenDingTalk,
                boolean shouldWaitForFreshLocation,
                boolean shouldWaitForUnlock,
                boolean shouldRecordPunchNow
        ) {
            this.action = action;
            this.shouldAttemptOpenDingTalk = shouldAttemptOpenDingTalk;
            this.shouldWaitForFreshLocation = shouldWaitForFreshLocation;
            this.shouldWaitForUnlock = shouldWaitForUnlock;
            this.shouldRecordPunchNow = shouldRecordPunchNow;
        }

        static FinalOutcome waitLocation() {
            return new FinalOutcome(GuardDecisionEngine.CheckInAction.WAIT_LOCATION, false, false, false, false);
        }

        static FinalOutcome waitFreshLocation() {
            return new FinalOutcome(GuardDecisionEngine.CheckInAction.WAIT_FRESH_LOCATION, false, true, false, false);
        }

        static FinalOutcome outsideTarget() {
            return new FinalOutcome(GuardDecisionEngine.CheckInAction.OUTSIDE_TARGET, false, false, false, false);
        }

        static FinalOutcome waitUnlock() {
            return new FinalOutcome(GuardDecisionEngine.CheckInAction.INSIDE_WAIT_UNLOCK, false, false, true, false);
        }

        static FinalOutcome openDingTalk() {
            return new FinalOutcome(GuardDecisionEngine.CheckInAction.INSIDE_OPEN_DINGTALK, true, false, false, false);
        }
    }
}
