package com.kanon.dingpunchguard;

import android.app.AppOpsManager;
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
        boolean overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
        Integer backgroundPopupMode = checkPrivateAppOp(context, MIUI_BACKGROUND_POPUP_OP);
        Integer showOnLockScreenMode = checkPrivateAppOp(context, MIUI_SHOW_ON_LOCK_SCREEN_OP);
        return new Status(overlayAllowed, backgroundPopupMode, showOnLockScreenMode);
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
        final boolean overlayAllowed;
        final Integer backgroundPopupMode;
        final Integer showOnLockScreenMode;

        Status(boolean overlayAllowed, Integer backgroundPopupMode, Integer showOnLockScreenMode) {
            this.overlayAllowed = overlayAllowed;
            this.backgroundPopupMode = backgroundPopupMode;
            this.showOnLockScreenMode = showOnLockScreenMode;
        }

        boolean likelyAllowed() {
            return overlayAllowed
                    && modeAllowedOrUnknown(backgroundPopupMode)
                    && modeAllowedOrUnknown(showOnLockScreenMode);
        }

        String logText() {
            return "overlayAllowed=" + overlayAllowed
                    + " miuiBackgroundPopup=" + modeText(backgroundPopupMode)
                    + " miuiShowOnLockScreen=" + modeText(showOnLockScreenMode)
                    + " likelyAllowed=" + likelyAllowed();
        }

        String displayText() {
            if (likelyAllowed()) {
                return "已放行";
            }
            return "未完全放行";
        }

        private static boolean modeAllowedOrUnknown(Integer mode) {
            return mode == null || mode == MODE_ALLOWED;
        }

        private static String modeText(Integer mode) {
            return mode == null ? "unknown" : String.valueOf(mode);
        }
    }
}
