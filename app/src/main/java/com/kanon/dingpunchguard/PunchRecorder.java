package com.kanon.dingpunchguard;

import android.content.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;

final class PunchRecorder {
    static final int KIND_UNKNOWN = 0;
    static final int KIND_CHECKIN = 1;
    static final int KIND_CHECKOUT = 2;

    private PunchRecorder() {
    }

    static boolean recordCheckIn(Context context, long millis, String source) {
        if (!ChinaWorkdayCalendar.isWorkday(LocalDate.now())) {
            NotificationHelper.postInfo(context, "今日休息", "非工作日不记录上班打卡。");
            return false;
        }
        if (Config.hasCheckedInToday(context)) {
            return false;
        }
        Config.markCheckIn(context, millis);
        NotificationHelper.cancelAlert(context);
        TimeScheduler.scheduleAll(context);
        NotificationHelper.postInfo(context, "已记录上班打卡", "来源：" + source);
        AppLog.i(context, "recorded check-in from " + source);
        return true;
    }

    static boolean recordCheckOut(Context context, long millis, String source) {
        if (!ChinaWorkdayCalendar.isWorkday(LocalDate.now())) {
            NotificationHelper.postInfo(context, "今日休息", "非工作日不记录下班打卡。");
            return false;
        }
        if (Config.hasCheckedOutToday(context)) {
            return false;
        }
        Config.markCheckOut(context, millis);
        NotificationHelper.cancelAlert(context);
        TimeScheduler.scheduleAll(context);
        NotificationHelper.postInfo(context, "已记录下班打卡", "来源：" + source);
        AppLog.i(context, "recorded check-out from " + source);
        return true;
    }

    static int inferKindFromTextOrSchedule(Context context, String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, "下班", "签退", "退勤")) {
            return KIND_CHECKOUT;
        }
        if (containsAny(normalized, "上班", "签到", "出勤")) {
            return KIND_CHECKIN;
        }

        long now = System.currentTimeMillis();
        if (!Config.hasCheckedOutToday(context)) {
            long due = TimeScheduler.checkoutDueMillis(context);
            long stop = due + Config.checkoutGraceMinutes(context) * 60_000L;
            if (due > 0L && now >= due - 30 * 60_000L && now <= stop) {
                return KIND_CHECKOUT;
            }
        }

        if (!Config.hasCheckedInToday(context) && isCheckInWindowNow(context)) {
            return KIND_CHECKIN;
        }
        if (Config.hasMissedCheckInToday(context)) {
            return KIND_UNKNOWN;
        }
        if (!Config.hasCheckedInToday(context)) {
            return KIND_CHECKIN;
        }
        if (!Config.hasCheckedOutToday(context)) {
            return KIND_CHECKOUT;
        }
        return KIND_UNKNOWN;
    }

    static boolean looksLikeDingTalkPunchSuccess(Context context, String text) {
        return looksLikeDingTalkPunchSuccess(text, recentVerifiedDingForeground(context));
    }

    static boolean looksLikeDingTalkPunchSuccess(String text, boolean recentVerifiedForeground) {
        String normalized = normalize(text);
        return successKeyword(normalized)
                && !failureKeyword(normalized)
                && (strongPunchContext(normalized) || recentVerifiedForeground);
    }

    static String dingTalkSuccessDebug(Context context, String text) {
        String normalized = normalize(text);
        return "successKeyword=" + successKeyword(normalized)
                + " strongContext=" + strongPunchContext(normalized)
                + " recentVerifiedForeground=" + recentVerifiedDingForeground(context)
                + " failureKeyword=" + failureKeyword(normalized)
                + " hasCheckIn=" + Config.hasCheckedInToday(context)
                + " hasCheckOut=" + Config.hasCheckedOutToday(context)
                + " missedCheckIn=" + Config.hasMissedCheckInToday(context);
    }

    private static boolean successKeyword(String normalized) {
        return containsAny(
                normalized,
                "打卡成功",
                "成功打卡",
                "已成功打卡",
                "已打卡",
                "已完成打卡",
                "打卡已完成",
                "已为你打卡",
                "已为你完成打卡",
                "考勤成功",
                "签到成功",
                "签退成功"
        ) || (normalized.contains("极速打卡")
                && containsAny(normalized, "成功", "已打卡", "已完成", "完成打卡"));
    }

    private static boolean strongPunchContext(String normalized) {
        return containsAny(
                normalized,
                "考勤",
                "钉钉",
                "极速打卡",
                "上班打卡",
                "下班打卡",
                "签到成功",
                "签退成功"
        );
    }

    private static boolean recentVerifiedDingForeground(Context context) {
        return System.currentTimeMillis() - Config.lastDingTalkVerifiedForegroundMillis(context) <= 10 * 60_000L;
    }

    private static boolean failureKeyword(String normalized) {
        return containsAny(normalized, "失败", "异常", "缺卡", "未打卡", "未确认", "待确认", "未检测到", "未弹出", "不能自动记录成功");
    }

    private static boolean isCheckInWindowNow(Context context) {
        LocalDate today = LocalDate.now();
        if (!ChinaWorkdayCalendar.isWorkday(today)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = today.atTime(Config.checkInStart(context)).minusMinutes(30);
        LocalDateTime end = today.atTime(Config.checkInStart(context)).plusMinutes(Config.lateMinutes(context));
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
