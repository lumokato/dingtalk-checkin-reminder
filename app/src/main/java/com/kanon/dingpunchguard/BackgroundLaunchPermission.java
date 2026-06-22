package com.kanon.dingpunchguard;

import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;

final class BackgroundLaunchPermission {
    private static final int MODE_ALLOWED = AppOpsManager.MODE_ALLOWED;
    private static final int MIUI_AUTO_START_OP = 10008;
    private static final int MIUI_BACKGROUND_POPUP_OP = 10021;

    private BackgroundLaunchPermission() {
    }

    static Status status(Context context) {
        NotificationHelper.ensureChannels(context);
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        boolean notificationsEnabled = notificationManager != null && notificationManager.areNotificationsEnabled();
        NotificationChannel alertChannel = notificationManager == null
                ? null
                : notificationManager.getNotificationChannel(NotificationHelper.ALERT_CHANNEL_ID);
        int alertChannelImportance = alertChannel == null
                ? NotificationManager.IMPORTANCE_NONE
                : alertChannel.getImportance();
        boolean alertChannelEnabled = alertChannelImportance != NotificationManager.IMPORTANCE_NONE;
        boolean alertChannelHighPriority = alertChannelImportance >= NotificationManager.IMPORTANCE_HIGH;
        boolean overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
        Integer autoStartMode = checkPrivateAppOp(context, MIUI_AUTO_START_OP);
        Integer backgroundPopupMode = checkPrivateAppOp(context, MIUI_BACKGROUND_POPUP_OP);
        return new Status(
                notificationsEnabled,
                alertChannelEnabled,
                alertChannelHighPriority,
                alertChannelImportance,
                overlayAllowed,
                autoStartMode,
                backgroundPopupMode
        );
    }

    private static Integer checkPrivateAppOp(Context context, int op) {
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            return null;
        }
        try {
            Method method = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            Object result = method.invoke(appOpsManager, op, Process.myUid(), context.getPackageName());
            return result instanceof Integer ? (Integer) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    static final class Status {
        final boolean notificationsEnabled;
        final boolean alertChannelEnabled;
        final boolean alertChannelHighPriority;
        final int alertChannelImportance;
        final boolean overlayAllowed;
        final Integer autoStartMode;
        final Integer backgroundPopupMode;

        Status(
                boolean notificationsEnabled,
                boolean alertChannelEnabled,
                boolean alertChannelHighPriority,
                int alertChannelImportance,
                boolean overlayAllowed,
                Integer autoStartMode,
                Integer backgroundPopupMode
        ) {
            this.notificationsEnabled = notificationsEnabled;
            this.alertChannelEnabled = alertChannelEnabled;
            this.alertChannelHighPriority = alertChannelHighPriority;
            this.alertChannelImportance = alertChannelImportance;
            this.overlayAllowed = overlayAllowed;
            this.autoStartMode = autoStartMode;
            this.backgroundPopupMode = backgroundPopupMode;
        }

        boolean likelyAllowed() {
            return overlayAllowed
                    && autoStartAllowed()
                    && backgroundPopupAllowed();
        }

        boolean autoStartAllowed() {
            return modeAllowedOrUnknown(autoStartMode);
        }

        boolean backgroundPopupAllowed() {
            return modeAllowedOrUnknown(backgroundPopupMode);
        }

        String alertChannelText() {
            if (!alertChannelEnabled) {
                return "已关闭";
            }
            if (!alertChannelHighPriority) {
                return "当前级别 " + alertChannelImportance + "，需要高优先级";
            }
            return "高优先级";
        }

        String overlayText() {
            return overlayAllowed ? "已允许" : "未允许，后台拉起会被系统拦截";
        }

        String autoStartText() {
            return appOpDisplayText(autoStartMode);
        }

        String backgroundPopupText() {
            return appOpDisplayText(backgroundPopupMode);
        }

        String logText() {
            return "notificationsEnabled=" + notificationsEnabled
                    + " alertChannelEnabled=" + alertChannelEnabled
                    + " alertChannelImportance=" + alertChannelImportance
                    + " alertChannelHighPriority=" + alertChannelHighPriority
                    + " overlayAllowed=" + overlayAllowed
                    + " miuiAutoStart=" + modeText(autoStartMode)
                    + " miuiBackgroundPopup=" + modeText(backgroundPopupMode)
                    + " likelyAllowed=" + likelyAllowed();
        }

        String displayText() {
            if (likelyAllowed()) {
                return "已放行";
            }
            if (!overlayAllowed) {
                return "悬浮窗/后台显示未放行";
            }
            if (!autoStartAllowed()) {
                return "MIUI自启动未放行";
            }
            if (!backgroundPopupAllowed()) {
                return "MIUI后台弹出未放行";
            }
            return "未完全放行";
        }

        private static boolean modeAllowedOrUnknown(Integer mode) {
            return mode == null || mode == MODE_ALLOWED;
        }

        private static String modeText(Integer mode) {
            return mode == null ? "unknown" : String.valueOf(mode);
        }

        private static String appOpDisplayText(Integer mode) {
            if (mode == null) {
                return "系统未返回，按已放行处理";
            }
            if (mode == MODE_ALLOWED) {
                return "已放行";
            }
            return "未放行，模式 " + mode;
        }
    }
}
