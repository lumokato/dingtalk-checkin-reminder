package com.kanon.dingpunchguard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void rootSuccessIgnoresBroadDingTalkShellWithOldPunchMessage() {
        String text = "我的信息 tau 北京岚间科贸发展有限公司 搜索 搜索 密聊 更多 日历 待办 DING "
                + "打卡 通话 更多 消息 未读 置顶 工作通知:北京岚间科贸发展有限公司 "
                + "08:56 考勤打卡: 08:56 极速打卡·成功 工作通知 08:17 下班打卡提醒";

        assertFalse(PunchRecorder.looksLikeDingTalkPunchSuccessRoot(text));
    }

    @Test
    public void rootSuccessAllowsCompactPunchPrompt() {
        assertTrue(PunchRecorder.looksLikeDingTalkPunchSuccessRoot("考勤 上班极速打卡成功 08:56 上班打卡 我知道了 查看统计"));
    }

    @Test
    public void checkoutTextDoesNotOverrideMorningCheckInSuccessOutsideCheckoutWindow() {
        String text = "考勤 上班极速打卡成功 08:56 上班打卡 我的信息 打卡 下班打卡";

        assertEquals(PunchRecorder.KIND_CHECKIN, PunchRecorder.inferExplicitSuccessKind(text));
    }

    @Test
    public void explicitCheckoutSuccessCanStillBeInferredFromSuccessText() {
        assertEquals(
                PunchRecorder.KIND_CHECKOUT,
                PunchRecorder.inferExplicitSuccessKind("考勤 下班打卡成功")
        );
    }
}
