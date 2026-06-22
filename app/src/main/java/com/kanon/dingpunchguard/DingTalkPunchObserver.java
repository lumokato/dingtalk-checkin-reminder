package com.kanon.dingpunchguard;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class DingTalkPunchObserver extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        AppLog.i(this, "DingTalk notification listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        Notification notification = sbn.getNotification();
        String packageName = nullToEmpty(sbn.getPackageName());
        String channelId = notification == null ? "" : nullToEmpty(notification.getChannelId());
        String text = collectText(notification);
        if (!Config.DINGTALK_PACKAGE.equals(packageName)) {
            return;
        }
        boolean successMatch = PunchRecorder.looksLikeDingTalkPunchSuccess(this, text);
        int kind = PunchRecorder.inferKindFromTextOrSchedule(this, text);
        AppLog.i(
                this,
                "DingTalk notification debug pkg=" + packageName
                        + " channel=" + channelId
                        + " id=" + sbn.getId()
                        + " tag=" + nullToEmpty(sbn.getTag())
                        + " kind=" + kind
                        + " successMatch=" + successMatch
                        + " " + PunchRecorder.dingTalkSuccessDebug(this, text)
                        + " text=" + summarize(text)
        );
        if (!successMatch) {
            return;
        }

        boolean recorded = false;
        if (kind == PunchRecorder.KIND_CHECKIN) {
            recorded = PunchRecorder.recordCheckIn(this, sbn.getPostTime(), "钉钉通知");
        } else if (kind == PunchRecorder.KIND_CHECKOUT) {
            recorded = PunchRecorder.recordCheckOut(this, sbn.getPostTime(), "钉钉通知");
        }

        if (recorded) {
            AppLog.i(this, "recognized DingTalk punch success notification kind=" + kind);
        } else {
            AppLog.i(this, "DingTalk punch success notification matched but record skipped kind=" + kind);
        }
    }

    private static String collectText(Notification notification) {
        if (notification == null) {
            return "";
        }
        Bundle extras = notification.extras;
        if (extras == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        append(builder, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(builder, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(builder, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        append(builder, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        append(builder, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                append(builder, line);
            }
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, CharSequence value) {
        if (value != null && value.length() > 0) {
            builder.append(value).append('\n');
        }
    }

    private static String summarize(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 180) {
            return oneLine;
        }
        return oneLine.substring(0, 180) + "...";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
