package com.kanon.dingpunchguard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ChinaWorkdayCalendar {
    private static final Set<LocalDate> ADJUSTED_WORKDAYS_2026 = unmodifiableDates(
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 10, 10)
    );

    private static final Set<LocalDate> PUBLIC_HOLIDAYS_2026 = buildPublicHolidays2026();

    private ChinaWorkdayCalendar() {
    }

    public static DayStatus status(LocalDate date) {
        if (ADJUSTED_WORKDAYS_2026.contains(date)) {
            return DayStatus.adjustedWorkday();
        }
        if (PUBLIC_HOLIDAYS_2026.contains(date)) {
            return DayStatus.publicHoliday();
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return DayStatus.restDay();
        }
        return DayStatus.workday();
    }

    public static boolean isWorkday(LocalDate date) {
        return status(date).isWorkday();
    }

    public static LocalDate nextWorkdayOnOrAfter(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < 400; i++) {
            if (isWorkday(cursor)) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        return date;
    }

    public static LocalDate nextWorkdayAfter(LocalDate date) {
        return nextWorkdayOnOrAfter(date.plusDays(1));
    }

    private static Set<LocalDate> buildPublicHolidays2026() {
        HashSet<LocalDate> dates = new HashSet<>();
        addRange(dates, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        addRange(dates, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23));
        addRange(dates, LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6));
        addRange(dates, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));
        addRange(dates, LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21));
        addRange(dates, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27));
        addRange(dates, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
        return Collections.unmodifiableSet(dates);
    }

    private static void addRange(Set<LocalDate> dates, LocalDate start, LocalDate endInclusive) {
        LocalDate cursor = start;
        while (!cursor.isAfter(endInclusive)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
    }

    private static Set<LocalDate> unmodifiableDates(LocalDate... dates) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(dates)));
    }

    public static final class DayStatus {
        private final String label;
        private final boolean workday;
        private final boolean adjustedWorkday;
        private final boolean publicHoliday;

        private DayStatus(String label, boolean workday, boolean adjustedWorkday, boolean publicHoliday) {
            this.label = label;
            this.workday = workday;
            this.adjustedWorkday = adjustedWorkday;
            this.publicHoliday = publicHoliday;
        }

        public String label() {
            return label;
        }

        public boolean isWorkday() {
            return workday;
        }

        public boolean isAdjustedWorkday() {
            return adjustedWorkday;
        }

        public boolean isPublicHoliday() {
            return publicHoliday;
        }

        private static DayStatus adjustedWorkday() {
            return new DayStatus("调休上班", true, true, false);
        }

        private static DayStatus workday() {
            return new DayStatus("工作日", true, false, false);
        }

        private static DayStatus publicHoliday() {
            return new DayStatus("休假", false, false, true);
        }

        private static DayStatus restDay() {
            return new DayStatus("休息日", false, false, false);
        }
    }
}
