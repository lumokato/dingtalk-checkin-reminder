package com.kanon.dingpunchguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Config.isEnabled(context)) {
            return;
        }
        String action = intent == null ? Config.ACTION_REFRESH : intent.getAction();
        AppLog.i(context, "alarm received action=" + action);
        Intent serviceIntent = new Intent(context, GuardService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
            AppLog.e(context, "failed to start guard from alarm action=" + action, ignored);
            NotificationHelper.postAlert(
                    context,
                    "打卡提醒需要你打开",
                    "系统没有允许后台启动守护服务。请点开应用后，它会继续检查打卡窗口。",
                    null
            );
        }
        TimeScheduler.scheduleAll(context);
    }
}
