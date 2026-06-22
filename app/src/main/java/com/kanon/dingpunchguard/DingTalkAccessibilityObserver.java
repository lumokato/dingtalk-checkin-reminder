package com.kanon.dingpunchguard;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class DingTalkAccessibilityObserver extends AccessibilityService {
    private static final long MIN_ROOT_SCAN_INTERVAL_MS = 700L;
    private static final long MIN_DEBUG_LOG_INTERVAL_MS = 10_000L;
    private static final long DUPLICATE_MATCH_WINDOW_MS = 30_000L;
    private static final int MAX_NODE_COUNT = 160;
    private static final int MAX_TEXT_CHARS = 5_000;

    private long lastRootScanMillis;
    private long lastDebugLogMillis;
    private long lastMatchedMillis;
    private int lastMatchedHash;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AppLog.i(this, "DingTalk accessibility observer connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !Config.DINGTALK_PACKAGE.contentEquals(event.getPackageName())) {
            return;
        }

        long now = System.currentTimeMillis();
        Config.markDingTalkVerifiedForeground(this, now);

        StringBuilder eventTextBuilder = new StringBuilder();
        appendEventText(eventTextBuilder, event);
        String eventText = eventTextBuilder.toString();
        StringBuilder rootTextBuilder = new StringBuilder();
        if (shouldScanRoot(event, now)) {
            lastRootScanMillis = now;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    NodeBudget budget = new NodeBudget();
                    appendNodeText(rootTextBuilder, root, budget);
                } finally {
                    root.recycle();
                }
            }
            if (rootTextBuilder.length() == 0) {
                appendDingTalkWindowText(rootTextBuilder);
            }
        }

        String rootText = rootTextBuilder.toString();
        String visibleText = eventText + rootText;
        if (visibleText.trim().isEmpty()) {
            maybeLogDebug(now, event, visibleText, false, PunchRecorder.KIND_UNKNOWN);
            return;
        }

        boolean eventSuccess = PunchRecorder.looksLikeDingTalkPunchSuccess(
                eventText,
                event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        );
        boolean rootSuccess = PunchRecorder.looksLikeDingTalkPunchSuccess(rootText, false);
        boolean successMatch = eventSuccess || rootSuccess;
        String matchedText = eventSuccess ? eventText : rootText;
        int kind = PunchRecorder.inferKindFromTextOrSchedule(this, matchedText);
        if (!successMatch) {
            maybeLogDebug(now, event, visibleText, false, kind);
            return;
        }

        int textHash = matchedText.hashCode();
        if (textHash == lastMatchedHash && now - lastMatchedMillis <= DUPLICATE_MATCH_WINDOW_MS) {
            return;
        }
        lastMatchedHash = textHash;
        lastMatchedMillis = now;

        boolean recorded = false;
        if (kind == PunchRecorder.KIND_CHECKIN) {
            recorded = PunchRecorder.recordCheckIn(this, now, "钉钉内部提示");
        } else if (kind == PunchRecorder.KIND_CHECKOUT) {
            recorded = PunchRecorder.recordCheckOut(this, now, "钉钉内部提示");
        }

        AppLog.i(
                this,
                "DingTalk accessibility success matched event="
                        + eventTypeName(event.getEventType())
                        + " kind=" + kind
                        + " recorded=" + recorded
                        + " textHash=" + Integer.toHexString(textHash)
                        + " eventTextMatch=" + eventSuccess
                        + " rootTextMatch=" + rootSuccess
                        + " " + PunchRecorder.dingTalkSuccessDebug(this, matchedText)
                        + " text=" + summarizeMatchedText(matchedText)
        );
    }

    @Override
    public void onInterrupt() {
        AppLog.i(this, "DingTalk accessibility observer interrupted");
    }

    @Override
    public void onDestroy() {
        AppLog.i(this, "DingTalk accessibility observer destroyed");
        super.onDestroy();
    }

    private boolean shouldScanRoot(AccessibilityEvent event, long now) {
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return true;
        }
        return now - lastRootScanMillis >= MIN_ROOT_SCAN_INTERVAL_MS;
    }

    private void maybeLogDebug(long now, AccessibilityEvent event, String text, boolean successMatch, int kind) {
        boolean importantEvent = event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                || text.length() > 0;
        if (!importantEvent && now - lastDebugLogMillis < MIN_DEBUG_LOG_INTERVAL_MS) {
            return;
        }
        lastDebugLogMillis = now;
        AppLog.i(
                this,
                "DingTalk accessibility debug event="
                        + eventTypeName(event.getEventType())
                        + " kind=" + kind
                        + " successMatch=" + successMatch
                        + " textLength=" + text.length()
                        + " textHash=" + Integer.toHexString(text.hashCode())
                        + " " + PunchRecorder.dingTalkSuccessDebug(this, text)
        );
    }

    private static void appendEventText(StringBuilder builder, AccessibilityEvent event) {
        for (CharSequence item : event.getText()) {
            append(builder, item);
        }
        append(builder, event.getContentDescription());
    }

    private static void appendNodeText(StringBuilder builder, AccessibilityNodeInfo node, NodeBudget budget) {
        if (node == null || budget.exhausted(builder)) {
            return;
        }
        budget.visited++;
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount && !budget.exhausted(builder); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    appendNodeText(builder, child, budget);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private void appendDingTalkWindowText(StringBuilder builder) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return;
        }
        NodeBudget budget = new NodeBudget();
        for (AccessibilityWindowInfo window : windows) {
            if (window == null || budget.exhausted(builder)) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            try {
                if (Config.DINGTALK_PACKAGE.contentEquals(root.getPackageName())) {
                    appendNodeText(builder, root, budget);
                }
            } finally {
                root.recycle();
            }
        }
    }

    private static void append(StringBuilder builder, CharSequence value) {
        if (value != null && value.length() > 0 && builder.length() < MAX_TEXT_CHARS) {
            builder.append(value).append('\n');
        }
    }

    private static String summarizeMatchedText(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 120) {
            return oneLine;
        }
        return oneLine.substring(0, 120) + "...";
    }

    private static String eventTypeName(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                return "notification";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "window_state";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "window_content";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                return "windows_changed";
            default:
                return String.valueOf(eventType);
        }
    }

    private static final class NodeBudget {
        int visited;

        boolean exhausted(StringBuilder builder) {
            return visited >= MAX_NODE_COUNT || builder.length() >= MAX_TEXT_CHARS;
        }
    }
}
