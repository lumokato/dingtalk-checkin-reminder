package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LaunchGateTest {
    @Test
    public void blocksWhenScreenIsOff() {
        assertFalse(LaunchGate.canAutoLaunch(false, false, 1_000L, 2_000L));
    }

    @Test
    public void blocksWhenKeyguardIsLocked() {
        assertFalse(LaunchGate.canAutoLaunch(true, true, 1_000L, 2_000L));
    }

    @Test
    public void allowsWhenScreenIsOnAndKeyguardIsClearWithoutUserPresentSignal() {
        assertTrue(LaunchGate.canAutoLaunch(true, false, 0L, 2_000L));
    }

    @Test
    public void allowsWhenUserPresentSignalIsStaleButCurrentDeviceStateIsUnlocked() {
        long now = 120_000L;
        long staleUserPresent = now - LaunchGate.USER_PRESENT_VALID_MILLIS - 1L;
        assertTrue(LaunchGate.canAutoLaunch(true, false, staleUserPresent, now));
    }

    @Test
    public void allowsRecentUserPresentWithClearKeyguard() {
        long now = 120_000L;
        long recentUserPresent = now - LaunchGate.USER_PRESENT_VALID_MILLIS;
        assertTrue(LaunchGate.canAutoLaunch(true, false, recentUserPresent, now));
    }
}
