package com.kanon.dingpunchguard;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ForegroundHistoryScenarioMatrixTest {
    private static final long OPEN_MILLIS = 10_000L;

    @Parameterized.Parameters(name = "{index}: afterOpen={0}, finalForeground={1}, oldDingTalkBeforeOpen={2}")
    public static Iterable<Object[]> data() {
        List<Object[]> cases = new ArrayList<>();
        for (AfterOpenSequence afterOpen : AfterOpenSequence.values()) {
            for (FinalForeground finalForeground : FinalForeground.values()) {
                for (boolean oldDingTalkBeforeOpen : new boolean[]{false, true}) {
                    cases.add(new Object[]{afterOpen, finalForeground, oldDingTalkBeforeOpen});
                }
            }
        }
        return cases;
    }

    private final AfterOpenSequence afterOpen;
    private final FinalForeground finalForeground;
    private final boolean oldDingTalkBeforeOpen;

    public ForegroundHistoryScenarioMatrixTest(
            AfterOpenSequence afterOpen,
            FinalForeground finalForeground,
            boolean oldDingTalkBeforeOpen
    ) {
        this.afterOpen = afterOpen;
        this.finalForeground = finalForeground;
        this.oldDingTalkBeforeOpen = oldDingTalkBeforeOpen;
    }

    @Test
    public void foregroundHistoryMatchesScenario() {
        ForegroundPackageHistory history = new ForegroundPackageHistory(Config.DINGTALK_PACKAGE, OPEN_MILLIS);
        if (oldDingTalkBeforeOpen) {
            history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS - 1_001L);
        }
        afterOpen.apply(history);
        finalForeground.apply(history);

        ForegroundOutcome expected = expectedOutcome();

        assertEquals(scenarioText(), expected.canFallbackRecordFromForeground, history.targetSeen());
        assertEquals(scenarioText(), expected.latestForegroundPackage, history.latestPackage());
    }

    private String scenarioText() {
        return "foreground scenario afterOpen=" + afterOpen
                + " finalForeground=" + finalForeground
                + " oldDingTalkBeforeOpen=" + oldDingTalkBeforeOpen;
    }

    private ForegroundOutcome expectedOutcome() {
        boolean finalForegroundIsDingTalk = Config.DINGTALK_PACKAGE.equals(finalForeground.packageName);
        boolean canFallbackRecord = afterOpen.includesDingTalkAfterOpen || finalForegroundIsDingTalk;
        String latestPackage = finalForeground.hasEvent()
                ? finalForeground.packageName
                : afterOpen.latestPackageName;
        return new ForegroundOutcome(canFallbackRecord, latestPackage);
    }

    enum AfterOpenSequence {
        NO_EVENTS(false, "") {
            @Override
            void apply(ForegroundPackageHistory history) {
            }
        },
        HOME_ONLY(false, "com.miui.home") {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept("com.miui.home", OPEN_MILLIS + 100L);
            }
        },
        OTHER_ONLY(false, "com.example.other") {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept("com.example.other", OPEN_MILLIS + 100L);
            }
        },
        DINGTALK_ONLY(true, Config.DINGTALK_PACKAGE) {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 100L);
            }
        },
        HOME_THEN_DINGTALK(true, Config.DINGTALK_PACKAGE) {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept("com.miui.home", OPEN_MILLIS + 100L);
                history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 200L);
            }
        },
        DINGTALK_THEN_THIS_APP(true, "com.kanon.dingpunchguard") {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 100L);
                history.accept("com.kanon.dingpunchguard", OPEN_MILLIS + 200L);
            }
        },
        DINGTALK_THEN_HOME(true, "com.miui.home") {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 100L);
                history.accept("com.miui.home", OPEN_MILLIS + 200L);
            }
        },
        DINGTALK_THEN_OTHER(true, "com.example.other") {
            @Override
            void apply(ForegroundPackageHistory history) {
                history.accept(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 100L);
                history.accept("com.example.other", OPEN_MILLIS + 200L);
            }
        };

        final boolean includesDingTalkAfterOpen;
        final String latestPackageName;

        AfterOpenSequence(boolean includesDingTalkAfterOpen, String latestPackageName) {
            this.includesDingTalkAfterOpen = includesDingTalkAfterOpen;
            this.latestPackageName = latestPackageName;
        }

        abstract void apply(ForegroundPackageHistory history);
    }

    enum FinalForeground {
        EMPTY("", -1L),
        DESKTOP("com.miui.home", OPEN_MILLIS + 1_000L),
        OTHER_APP("com.example.other", OPEN_MILLIS + 1_000L),
        DINGTALK(Config.DINGTALK_PACKAGE, OPEN_MILLIS + 1_000L),
        THIS_APP("com.kanon.dingpunchguard", OPEN_MILLIS + 1_000L);

        final String packageName;
        final long eventTimeMillis;

        FinalForeground(String packageName, long eventTimeMillis) {
            this.packageName = packageName;
            this.eventTimeMillis = eventTimeMillis;
        }

        void apply(ForegroundPackageHistory history) {
            if (eventTimeMillis >= 0L) {
                history.accept(packageName, eventTimeMillis);
            }
        }

        boolean hasEvent() {
            return eventTimeMillis >= 0L;
        }
    }

    static final class ForegroundOutcome {
        final boolean canFallbackRecordFromForeground;
        final String latestForegroundPackage;

        ForegroundOutcome(boolean canFallbackRecordFromForeground, String latestForegroundPackage) {
            this.canFallbackRecordFromForeground = canFallbackRecordFromForeground;
            this.latestForegroundPackage = latestForegroundPackage;
        }
    }
}
