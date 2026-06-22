package com.kanon.dingpunchguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    private static final String ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        boolean userPresent = Intent.ACTION_USER_PRESENT.equals(action);
        boolean schedulerEvent = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || ACTION_EXACT_ALARM_PERMISSION_CHANGED.equals(action);
        if (!schedulerEvent && !userPresent) {
            return;
        }
        if (!Config.isEnabled(context)) {
            return;
        }
        AppLog.i(context, "boot receiver action=" + action + " userPresent=" + userPresent + " schedulerEvent=" + schedulerEvent);
        if (schedulerEvent) {
            TimeScheduler.scheduleAll(context);
        }
        if (userPresent && GuardService.isWindowActiveNow(context)) {
            startGuardForUserPresent(context);
        }
    }

    private void startGuardForUserPresent(Context context) {
        Intent serviceIntent = new Intent(context, GuardService.class).setAction(Config.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            AppLog.e(context, "failed to start guard on user present", e);
            NotificationHelper.postAlert(
                    context,
                    "打开打卡提醒",
                    "系统没有允许解锁后自动启动检测。请点开应用，它会立即检查是否需要打开钉钉。",
                    null
            );
        }
    }
}
