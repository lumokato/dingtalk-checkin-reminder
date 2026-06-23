package com.kanon.dingpunchguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class Config {
    static final String ACTION_START = "com.kanon.dingpunchguard.START";
    static final String ACTION_STOP = "com.kanon.dingpunchguard.STOP";
    static final String ACTION_ALARM_CHECKIN = "com.kanon.dingpunchguard.ALARM_CHECKIN";
    static final String ACTION_ALARM_CHECKOUT = "com.kanon.dingpunchguard.ALARM_CHECKOUT";
    static final String ACTION_OPEN_DING = "com.kanon.dingpunchguard.OPEN_DING";
    static final String ACTION_CONFIRM_CHECKIN = "com.kanon.dingpunchguard.CONFIRM_CHECKIN";
    static final String ACTION_CONFIRM_CHECKOUT = "com.kanon.dingpunchguard.CONFIRM_CHECKOUT";
    static final String ACTION_REFRESH = "com.kanon.dingpunchguard.REFRESH";
    static final String ACTION_USER_PRESENT = "com.kanon.dingpunchguard.USER_PRESENT";
    static final String ACTION_CLEAR_TODAY_STATE = "com.kanon.dingpunchguard.CLEAR_TODAY_STATE";
    static final String ACTION_CLEAR_TODAY_CHECKOUT_STATE = "com.kanon.dingpunchguard.CLEAR_TODAY_CHECKOUT_STATE";

    static final String DINGTALK_PACKAGE = "com.alibaba.android.rimet";
    static final int FOREGROUND_NOTIFICATION_ID = 1001;
    static final int ALERT_NOTIFICATION_ID = 1002;
    static final int INFO_NOTIFICATION_ID = 1003;

    private static final String PREFS = "guard_settings";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private Config() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean("enabled", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("enabled", enabled).apply();
    }

    static void migrate(Context context) {
        prefs(context).edit()
                .remove("checkin_end")
                .remove("work_minutes")
                .apply();
    }

    static String placeName(Context context) {
        return prefs(context).getString("place_name", "公司");
    }

    static double gcjLat(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong("gcj_lat", Double.doubleToLongBits(0.0d)));
    }

    static double gcjLon(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong("gcj_lon", Double.doubleToLongBits(0.0d)));
    }

    static int radiusMeters(Context context) {
        return prefs(context).getInt("radius_meters", 300);
    }

    static LocalTime checkInStart(Context context) {
        return parseTime(prefs(context).getString("checkin_start", "08:30"), LocalTime.of(8, 30));
    }

    static int lateMinutes(Context context) {
        return Math.max(0, prefs(context).getInt("late_minutes", 30));
    }

    static LocalTime checkoutTime(Context context) {
        return parseTime(prefs(context).getString("checkout_time", "18:00"), LocalTime.of(18, 0));
    }

    static int reminderMinutes(Context context) {
        return Math.max(1, prefs(context).getInt("reminder_minutes", 2));
    }

    static int checkoutGraceMinutes(Context context) {
        return Math.max(30, prefs(context).getInt("checkout_grace_minutes", 180));
    }

    static boolean requireLocationForCheckout(Context context) {
        return prefs(context).getBoolean("checkout_requires_location", false);
    }

    static boolean assumeDingTalkOpenMeansSuccess(Context context) {
        return prefs(context).getBoolean("assume_open_success", false);
    }

    static long lastDingTalkOpenMillis(Context context) {
        return prefs(context).getLong("last_dingtalk_open_millis", 0L);
    }

    static void markDingTalkOpened(Context context, long millis) {
        prefs(context).edit()
                .putLong("last_dingtalk_open_millis", millis)
                .apply();
    }

    static long lastDingTalkVerifiedForegroundMillis(Context context) {
        return prefs(context).getLong("last_dingtalk_verified_foreground_millis", 0L);
    }

    static void markDingTalkVerifiedForeground(Context context, long millis) {
        prefs(context).edit()
                .putLong("last_dingtalk_verified_foreground_millis", millis)
                .apply();
    }

    static boolean isLocationConfigured(Context context) {
        double lat = gcjLat(context);
        double lon = gcjLon(context);
        return Math.abs(lat) > 0.000001d || Math.abs(lon) > 0.000001d;
    }

    static void saveUserSettings(
            Context context,
            String placeName,
            double gcjLat,
            double gcjLon,
            int radiusMeters,
            String checkInStart,
            int lateMinutes,
            String checkoutTime,
            int reminderMinutes,
            int checkoutGraceMinutes,
            boolean checkoutRequiresLocation,
            boolean assumeOpenSuccess
    ) {
        prefs(context).edit()
                .putString("place_name", placeName)
                .putLong("gcj_lat", Double.doubleToLongBits(gcjLat))
                .putLong("gcj_lon", Double.doubleToLongBits(gcjLon))
                .putInt("radius_meters", radiusMeters)
                .putString("checkin_start", normalizeTime(checkInStart, "08:30"))
                .putInt("late_minutes", Math.max(0, lateMinutes))
                .putString("checkout_time", normalizeTime(checkoutTime, "18:00"))
                .remove("checkin_end")
                .remove("work_minutes")
                .putInt("reminder_minutes", reminderMinutes)
                .putInt("checkout_grace_minutes", checkoutGraceMinutes)
                .putBoolean("checkout_requires_location", checkoutRequiresLocation)
                .putBoolean("assume_open_success", assumeOpenSuccess)
                .apply();
    }

    static String todayKey() {
        return LocalDate.now().toString();
    }

    static boolean hasCheckedInToday(Context context) {
        return todayKey().equals(prefs(context).getString("checkin_date", ""));
    }

    static boolean hasMissedCheckInToday(Context context) {
        return todayKey().equals(prefs(context).getString("checkin_missed_date", ""));
    }

    static boolean hasCheckedOutToday(Context context) {
        return todayKey().equals(prefs(context).getString("checkout_date", ""));
    }

    static long checkInMillis(Context context) {
        return prefs(context).getLong("checkin_millis", 0L);
    }

    static long checkOutMillis(Context context) {
        return prefs(context).getLong("checkout_millis", 0L);
    }

    static void markCheckIn(Context context, long millis) {
        prefs(context).edit()
                .putString("checkin_date", todayKey())
                .putLong("checkin_millis", millis)
                .remove("checkin_missed_date")
                .apply();
    }

    static void markCheckInMissed(Context context) {
        prefs(context).edit()
                .putString("checkin_missed_date", todayKey())
                .apply();
    }

    static void markCheckOut(Context context, long millis) {
        prefs(context).edit()
                .putString("checkout_date", todayKey())
                .putLong("checkout_millis", millis)
                .apply();
    }

    static void clearTodayState(Context context) {
        prefs(context).edit()
                .remove("checkin_date")
                .remove("checkin_millis")
                .remove("checkin_missed_date")
                .remove("checkout_date")
                .remove("checkout_millis")
                .remove("last_dingtalk_open_millis")
                .remove("last_dingtalk_verified_foreground_millis")
                .apply();
    }

    static void clearTodayCheckOutState(Context context) {
        prefs(context).edit()
                .remove("checkout_date")
                .remove("checkout_millis")
                .apply();
    }

    static LocalTime parseTime(String value, LocalTime fallback) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception ignored) {
            try {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception alsoIgnored) {
                String digits = value == null ? "" : value.replaceAll("\\D", "");
                if (digits.length() == 3 || digits.length() == 4) {
                    try {
                        int hour = Integer.parseInt(digits.substring(0, digits.length() - 2));
                        int minute = Integer.parseInt(digits.substring(digits.length() - 2));
                        return LocalTime.of(hour, minute);
                    } catch (Exception numericIgnored) {
                    }
                }
            }
            return fallback;
        }
    }

    static String format(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    private static String normalizeTime(String value, String fallback) {
        return format(parseTime(value, parseTime(fallback, LocalTime.of(8, 30))));
    }
}
