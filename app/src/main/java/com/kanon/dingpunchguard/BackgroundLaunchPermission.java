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
    private static final int MIUI_BACKGROUND_POPUP_OP = 10021;
    private static final int MIUI_SHOW_ON_LOCK_SCREEN_OP = 10008;

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
        boolean fullScreenIntentAllowed = canUseFullScreenIntent(context);
        Integer backgroundPopupMode = checkPrivateAppOp(context, MIUI_BACKGROUND_POPUP_OP);
        Integer showOnLockScreenMode = checkPrivateAppOp(context, MIUI_SHOW_ON_LOCK_SCREEN_OP);
        return new Status(
                notificationsEnabled,
                alertChannelEnabled,
                alertChannelHighPriority,
                alertChannelImportance,
                overlayAllowed,
                fullScreenIntentAllowed,
                backgroundPopupMode,
                showOnLockScreenMode
        );
    }

    private static boolean canUseFullScreenIntent(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        return notificationManager != null && notificationManager.canUseFullScreenIntent();
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
        final boolean fullScreenIntentAllowed;
        final Integer backgroundPopupMode;
        final Integer showOnLockScreenMode;

        Status(
                boolean notificationsEnabled,
                boolean alertChannelEnabled,
                boolean alertChannelHighPriority,
                int alertChannelImportance,
                boolean overlayAllowed,
                boolean fullScreenIntentAllowed,
                Integer backgroundPopupMode,
                Integer showOnLockScreenMode
        ) {
            this.notificationsEnabled = notificationsEnabled;
            this.alertChannelEnabled = alertChannelEnabled;
            this.alertChannelHighPriority = alertChannelHighPriority;
            this.alertChannelImportance = alertChannelImportance;
            this.overlayAllowed = overlayAllowed;
            this.fullScreenIntentAllowed = fullScreenIntentAllowed;
            this.backgroundPopupMode = backgroundPopupMode;
            this.showOnLockScreenMode = showOnLockScreenMode;
        }

        boolean likelyAllowed() {
            return notificationsEnabled
                    && alertChannelEnabled
                    && alertChannelHighPriority
                    && fullScreenIntentAllowed
                    && backgroundPopupAllowed()
                    && showOnLockScreenAllowed();
        }

        boolean backgroundPopupAllowed() {
            return modeAllowedOrUnknown(backgroundPopupMode);
        }

        boolean showOnLockScreenAllowed() {
            return modeAllowedOrUnknown(showOnLockScreenMode);
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

        String backgroundPopupText() {
            return appOpDisplayText(backgroundPopupMode);
        }

        String showOnLockScreenText() {
            return appOpDisplayText(showOnLockScreenMode);
        }

        String logText() {
            return "notificationsEnabled=" + notificationsEnabled
                    + " alertChannelEnabled=" + alertChannelEnabled
                    + " alertChannelImportance=" + alertChannelImportance
                    + " alertChannelHighPriority=" + alertChannelHighPriority
                    + " overlayAllowed=" + overlayAllowed
                    + " fullScreenIntentAllowed=" + fullScreenIntentAllowed
                    + " miuiBackgroundPopup=" + modeText(backgroundPopupMode)
                    + " miuiShowOnLockScreen=" + modeText(showOnLockScreenMode)
                    + " likelyAllowed=" + likelyAllowed();
        }

        String displayText() {
            if (likelyAllowed()) {
                return "已放行";
            }
            if (!notificationsEnabled) {
                return "通知总开关未放行";
            }
            if (!alertChannelEnabled) {
                return "强提醒渠道已关闭";
            }
            if (!alertChannelHighPriority) {
                return "强提醒不是高优先级";
            }
            if (!fullScreenIntentAllowed) {
                return "全屏通知未放行";
            }
            if (!backgroundPopupAllowed() || !showOnLockScreenAllowed()) {
                return "后台弹出未放行";
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
