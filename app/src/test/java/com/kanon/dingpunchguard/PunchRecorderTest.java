package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PunchRecorderTest {
    @Test
    public void strongDingTalkPunchSuccessTextMatchesWithoutForegroundFallback() {
        assertTrue(PunchRecorder.looksLikeDingTalkPunchSuccess("钉钉 极速打卡 打卡成功", false));
        assertTrue(PunchRecorder.looksLikeDingTalkPunchSuccess("考勤 签退成功", false));
        assertTrue(PunchRecorder.looksLikeDingTalkPunchSuccess("极速打卡 已为你完成打卡", false));
    }

    @Test
    public void plainPunchSuccessRequiresRecentVerifiedDingTalkForeground() {
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("打卡成功", false));
        assertTrue(PunchRecorder.looksLikeDingTalkPunchSuccess("打卡成功", true));
    }

    @Test
    public void failureWordsBlockSuccessMatch() {
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("钉钉 打卡成功 但存在异常", true));
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("考勤 未打卡", true));
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("极速打卡 失败", true));
    }

    @Test
    public void unrelatedSuccessTextDoesNotMatch() {
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("发送成功", true));
        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccess("审批成功", true));
    }
}
