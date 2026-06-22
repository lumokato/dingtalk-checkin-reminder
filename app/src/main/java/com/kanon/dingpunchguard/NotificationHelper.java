package com.kanon.dingpunchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

final class NotificationHelper {
    private static final String GUARD_CHANNEL_ID = "guard_status";
    private static final String ALERT_CHANNEL_ID = "punch_alert";

    private NotificationHelper() {
    }

    static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        NotificationChannel guard = new NotificationChannel(
                GUARD_CHANNEL_ID,
                "上下班提醒运行状态",
                NotificationManager.IMPORTANCE_LOW
        );
        guard.setDescription("显示上下班提醒是否正在等待上班或下班");
        guard.setShowBadge(false);
        manager.createNotificationChannel(guard);

        NotificationChannel alert = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "打卡强提醒",
                NotificationManager.IMPORTANCE_HIGH
        );
        alert.setDescription("上班或下班需要打开钉钉时提醒");
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 500, 250, 500});
        alert.enableLights(true);
        alert.setLightColor(Color.rgb(78, 111, 143));
        manager.createNotificationChannel(alert);
    }

    static Notification foreground(Context context, String text) {
        Intent contentIntent = new Intent(context, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                10,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent openPendingIntent = openDingTalkServicePendingIntent(context, 11);
        PendingIntent stopPendingIntent = servicePendingIntent(context, 12, Config.ACTION_STOP);

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_punch)
                .setContentTitle("上下班提醒")
                .setContentText(text)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setShowWhen(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_stat_punch, "打开钉钉", openPendingIntent)
                .addAction(R.drawable.ic_stat_punch, "暂停提醒", stopPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(GUARD_CHANNEL_ID);
        } else {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        return builder.build();
    }

    static void postAlert(Context context, String title, String text, String confirmAction) {
        ensureChannels(context);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                20,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_punch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(R.drawable.ic_stat_punch, "打开钉钉", openDingTalkServicePendingIntent(context, 21));

        if (confirmAction != null) {
            String label = Config.ACTION_CONFIRM_CHECKIN.equals(confirmAction) ? "已上班打卡" : "已下班打卡";
            builder.addAction(R.drawable.ic_stat_punch, label, servicePendingIntent(context, 22, confirmAction));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(ALERT_CHANNEL_ID);
        } else {
            builder.setPriority(Notification.PRIORITY_HIGH);
            builder.setVibrate(new long[]{0, 500, 250, 500});
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(Config.ALERT_NOTIFICATION_ID, builder.build());
    }

    static boolean postDingTalkLaunchRequest(Context context, String title, String text, String confirmAction) {
        ensureChannels(context);
        PendingIntent dingTalkPendingIntent = dingTalkFullScreenPendingIntentOrNull(context, 41);
        if (dingTalkPendingIntent == null) {
            postAlert(context, "没有找到钉钉", "未安装钉钉，或系统不允许查询钉钉包名。", confirmAction);
            return false;
        }
        PendingIntent openThroughServicePendingIntent = openDingTalkServicePendingIntent(context, 43);

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_punch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openThroughServicePendingIntent)
                .setFullScreenIntent(dingTalkPendingIntent, true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(R.drawable.ic_stat_punch, "打开钉钉", openThroughServicePendingIntent);

        if (confirmAction != null) {
            String label = Config.ACTION_CONFIRM_CHECKIN.equals(confirmAction) ? "已上班打卡" : "已下班打卡";
            builder.addAction(R.drawable.ic_stat_punch, label, servicePendingIntent(context, 42, confirmAction));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(ALERT_CHANNEL_ID);
        } else {
            builder.setPriority(Notification.PRIORITY_MAX);
            builder.setVibrate(new long[]{0, 500, 250, 500});
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(Config.ALERT_NOTIFICATION_ID, builder.build());
        return true;
    }

    static void cancelAlert(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(Config.ALERT_NOTIFICATION_ID);
    }

    static void postInfo(Context context, String title, String text) {
        ensureChannels(context);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                30,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_stat_punch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_STATUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(ALERT_CHANNEL_ID);
        } else {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(Config.INFO_NOTIFICATION_ID, builder.build());
    }

    static void startForeground(Service service, String text, boolean withLocationType) {
        Notification notification = foreground(service, text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && withLocationType) {
            try {
                service.startForeground(
                        Config.FOREGROUND_NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                );
            } catch (SecurityException e) {
                service.startForeground(Config.FOREGROUND_NOTIFICATION_ID, notification);
            }
        } else {
            service.startForeground(Config.FOREGROUND_NOTIFICATION_ID, notification);
        }
    }

    private static PendingIntent servicePendingIntent(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, GuardService.class).setAction(action);
        return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent openDingTalkServicePendingIntent(Context context, int requestCode) {
        return servicePendingIntent(context, requestCode, Config.ACTION_OPEN_DING);
    }

    private static PendingIntent dingTalkFullScreenPendingIntentOrNull(Context context, int requestCode) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(Config.DINGTALK_PACKAGE);
        if (intent == null) {
            return null;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
