package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForegroundPackageHistoryTest {
    @Test
    public void targetSeenDoesNotRequireTargetToRemainLatestForeground() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, 10_000L);

        history.accept(Config.DINGTALK_PACKAGE, 10_500L);
        history.accept("com.kanon.dingpunchguard", 12_000L);
        history.accept("com.miui.home", 13_000L);

        assertTrue(history.targetSeen());
        assertEquals("com.miui.home", history.latestPackage());
    }

    @Test
    public void targetSeenIgnoresEventsBeforeLaunchWindow() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, 10_000L);

        history.accept(Config.DINGTALK_PACKAGE, 8_999L);
        history.accept("com.miui.home", 10_100L);

        assertFalse(history.targetSeen());
        assertEquals("com.miui.home", history.latestPackage());
    }

    @Test
    public void targetSeenAcceptsOneSecondToleranceAroundLaunchTime() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, 10_000L);

        history.accept(Config.DINGTALK_PACKAGE, 9_000L);

        assertTrue(history.targetSeen());
        assertEquals(Config.DINGTALK_PACKAGE, history.latestPackage());
    }

    @Test
    public void latestPackageUsesNewestForegroundEventEvenWhenEventsArriveOutOfOrder() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, 10_000L);

        history.accept("com.miui.home", 13_000L);
        history.accept(Config.DINGTALK_PACKAGE, 11_000L);

        assertTrue(history.targetSeen());
        assertEquals("com.miui.home", history.latestPackage());
    }

    @Test
    public void nullPackageNamesDoNotCrashHistory() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, 10_000L);

        history.accept(null, 10_100L);

        assertFalse(history.targetSeen());
        assertEquals("", history.latestPackage());
    }
}
