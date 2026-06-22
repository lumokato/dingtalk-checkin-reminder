package com.kanon.dingpunchguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class TimeScheduler {
    private static final int REQUEST_CHECKIN = 3001;
    private static final int REQUEST_CHECKOUT = 3002;
    private static final long MIN_REFRESH_MILLIS = 60_000L;
    private static final int EARLY_CHECKIN_WINDOW_MINUTES = 30;

    private TimeScheduler() {
    }

    static void scheduleAll(Context context) {
        cancelAll(context);
        if (!Config.isEnabled(context)) {
            AppLog.i(context, "schedule skipped because guard disabled");
            return;
        }
        markExpiredCheckInIfNeeded(context);
        scheduleCheckIn(context);
        scheduleCheckoutIfKnown(context);
    }

    static void cancelAll(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.cancel(pendingIntent(context, REQUEST_CHECKIN, Config.ACTION_ALARM_CHECKIN));
        alarmManager.cancel(pendingIntent(context, REQUEST_CHECKOUT, Config.ACTION_ALARM_CHECKOUT));
    }

    private static void scheduleCheckIn(Context context) {
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atTime(Config.checkInStart(context)).minusMinutes(EARLY_CHECKIN_WINDOW_MINUTES);
        LocalDateTime activeEndToday = today.atTime(Config.checkInStart(context)).plusMinutes(Config.lateMinutes(context));
        LocalDateTime now = LocalDateTime.now();

        if (!ChinaWorkdayCalendar.isWorkday(today)) {
            AppLog.i(context, "check-in schedule skipped today; next workday");
            scheduleNextCheckIn(context, today);
            return;
        }

        if (Config.hasCheckedInToday(context)) {
            AppLog.i(context, "check-in already recorded; scheduling next workday");
            scheduleNextCheckIn(context, today);
            return;
        }

        if (now.isBefore(startToday)) {
            AppLog.i(context, "check-in alarm scheduled at " + startToday);
            schedule(context, REQUEST_CHECKIN, Config.ACTION_ALARM_CHECKIN, toMillis(startToday));
            return;
        }

        if (!now.isAfter(activeEndToday)) {
            long target = Math.min(
                    System.currentTimeMillis() + refreshIntervalMillis(context),
                    toMillis(activeEndToday) + 1_000L
            );
            AppLog.i(context, "check-in window active; refresh alarm scheduled at " + LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(target), ZoneId.systemDefault()));
            schedule(context, REQUEST_CHECKIN, Config.ACTION_ALARM_CHECKIN, Math.max(target, System.currentTimeMillis() + 1_000L));
            return;
        }

        AppLog.i(context, "check-in window passed; scheduling next workday");
        scheduleNextCheckIn(context, today);
    }

    static boolean markExpiredCheckInIfNeeded(Context context) {
        LocalDate today = LocalDate.now();
        if (!ChinaWorkdayCalendar.isWorkday(today)) {
            return false;
        }
        if (Config.hasCheckedInToday(context) || Config.hasMissedCheckInToday(context)) {
            return false;
        }
        LocalDateTime activeEndToday = today.atTime(Config.checkInStart(context)).plusMinutes(Config.lateMinutes(context));
        if (!LocalDateTime.now().isAfter(activeEndToday)) {
            return false;
        }
        Config.markCheckInMissed(context);
        NotificationHelper.cancelAlert(context);
        AppLog.i(context, "check-in marked missed after window end " + activeEndToday);
        return true;
    }

    private static void scheduleCheckoutIfKnown(Context context) {
        if (!ChinaWorkdayCalendar.isWorkday(LocalDate.now())) {
            return;
        }
        if (Config.hasCheckedOutToday(context)) {
            return;
        }
        long due = checkoutDueMillis(context);
        if (due <= 0L) {
            return;
        }
        long stop = due + Config.checkoutGraceMinutes(context) * 60_000L;
        long now = System.currentTimeMillis();
        if (now > stop) {
            AppLog.i(context, "checkout schedule skipped because window passed");
            return;
        }

        long target = now < due
                ? due
                : Math.min(now + refreshIntervalMillis(context), stop + 1_000L);
        AppLog.i(context, "checkout alarm scheduled at " + LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Math.max(target, now + 1_000L)), ZoneId.systemDefault()));
        schedule(context, REQUEST_CHECKOUT, Config.ACTION_ALARM_CHECKOUT, Math.max(target, now + 1_000L));
    }

    static long checkoutDueMillis(Context context) {
        if (!ChinaWorkdayCalendar.isWorkday(LocalDate.now())) {
            return 0L;
        }
        if (Config.hasCheckedOutToday(context)) {
            return 0L;
        }
        if (Config.hasCheckedInToday(context)) {
            return plannedCheckoutMillis(context);
        }
        return plannedCheckoutMillis(context);
    }

    static long plannedCheckoutMillis(Context context) {
        LocalDate today = LocalDate.now();
        long baseCheckout = toMillis(today.atTime(Config.checkoutTime(context)));
        if (!Config.hasCheckedInToday(context)) {
            return baseCheckout;
        }
        long normalCheckIn = toMillis(today.atTime(Config.checkInStart(context)));
        long lateMillis = Math.max(0L, Config.checkInMillis(context) - normalCheckIn);
        return baseCheckout + lateMillis;
    }

    private static void scheduleNextCheckIn(Context context, LocalDate today) {
        LocalDate nextWorkday = ChinaWorkdayCalendar.nextWorkdayAfter(today);
        AppLog.i(context, "next check-in alarm scheduled at " + nextWorkday.atTime(Config.checkInStart(context)).minusMinutes(EARLY_CHECKIN_WINDOW_MINUTES));
        schedule(
                context,
                REQUEST_CHECKIN,
                Config.ACTION_ALARM_CHECKIN,
                toMillis(nextWorkday.atTime(Config.checkInStart(context)).minusMinutes(EARLY_CHECKIN_WINDOW_MINUTES))
        );
    }

    private static long refreshIntervalMillis(Context context) {
        return Math.max(MIN_REFRESH_MILLIS, Config.reminderMinutes(context) * 60_000L);
    }

    private static void schedule(Context context, int requestCode, String action, long triggerAtMillis) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = pendingIntent(context, requestCode, action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60_000L, pendingIntent);
            return;
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    private static PendingIntent pendingIntent(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static long toMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
