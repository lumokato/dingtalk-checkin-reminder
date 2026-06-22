package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocationFreshnessTest {
    @Test
    public void missingLocationIsStale() {
        assertTrue(LocationFreshness.isStale(false, 0L, 120_000L));
    }

    @Test
    public void ageAtLimitIsStillUsable() {
        assertFalse(LocationFreshness.isStale(true, 120_000L, 120_000L));
    }

    @Test
    public void agePastLimitIsStale() {
        assertTrue(LocationFreshness.isStale(true, 120_001L, 120_000L));
    }
}
