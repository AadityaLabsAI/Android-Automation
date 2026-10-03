package com.aadityalabs.needle2;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {
    private static final Pattern IN = Pattern.compile(
            "^in\\s+(\\d+)\\s*(minute|minutes|min|hour|hours|day|days)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AT = Pattern.compile(
            "^(?:(today|tomorrow)\\s+)?at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EVERY = Pattern.compile(
            "^every\\s+(\\d+)\\s*(minute|minutes|min|hour|hours|day|days)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DAILY = Pattern.compile(
            "^(daily|every day)(?:\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEKDAYS = Pattern.compile(
            "^weekdays\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

    private TimeParser() {
    }

    public static long parseWhen(String value, long nowMillis) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            return 0;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone);

        Matcher in = IN.matcher(input);
        if (in.matches()) {
            try {
                long amount = Long.parseLong(in.group(1));
                if (amount <= 0) return 0;

                String unit = in.group(2).toLowerCase(Locale.US);
                LocalDateTime target = unit.startsWith("hour")
                        ? now.plusHours(amount)
                        : unit.startsWith("day")
                        ? now.plusDays(amount)
                        : now.plusMinutes(amount);
                return target.atZone(zone).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
                return 0;
            }
        }

        try {
            return LocalDateTime.parse(input, ISO).atZone(zone).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }

        Matcher at = AT.matcher(input);
        if (!at.matches()) {
            return 0;
        }

        try {
            int hour = Integer.parseInt(at.group(2));
            int minute = at.group(3) == null ? 0 : Integer.parseInt(at.group(3));
            String meridiem = at.group(4);

            if (meridiem != null) {
                if (hour < 1 || hour > 12) return 0;
                if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
                if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            } else if (hour < 0 || hour > 23) {
                return 0;
            }

            if (minute < 0 || minute > 59) return 0;

            LocalDate date = "tomorrow".equalsIgnoreCase(at.group(1))
                    ? now.toLocalDate().plusDays(1)
                    : now.toLocalDate();

            LocalDateTime target = LocalDateTime.of(date, LocalTime.of(hour, minute));
            if (!target.isAfter(now) && !"tomorrow".equalsIgnoreCase(at.group(1))) {
                target = target.plusDays(1);
            }
            return target.atZone(zone).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static long nextRepeat(String value, long afterMillis) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            return 0;
        }

        Matcher every = EVERY.matcher(input);
        if (every.matches()) {
            try {
                long amount = Long.parseLong(every.group(1));
                if (amount <= 0) return 0;

                String unit = every.group(2).toLowerCase(Locale.US);
                long multiplier = unit.startsWith("hour")
                        ? 3_600_000L
                        : unit.startsWith("day")
                        ? 86_400_000L
                        : 60_000L;
                if (amount > Long.MAX_VALUE / multiplier) return 0;
                return afterMillis + amount * multiplier;
            } catch (RuntimeException ignored) {
                return 0;
            }
        }

        Matcher daily = DAILY.matcher(input);
        if (daily.matches()) {
            return dailyTarget(daily.group(2), daily.group(3), daily.group(4), afterMillis, false);
        }

        Matcher weekdays = WEEKDAYS.matcher(input);
        if (weekdays.matches()) {
            return dailyTarget(weekdays.group(1), weekdays.group(2), weekdays.group(3), afterMillis, true);
        }

        return 0;
    }

    private static long dailyTarget(
            String hourText, String minuteText, String meridiem, long afterMillis, boolean weekdays) {
        try {
            int hour = hourText == null ? 9 : Integer.parseInt(hourText);
            int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);

            if (meridiem != null) {
                if (hour < 1 || hour > 12) return 0;
                if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
                if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            } else if (hour < 0 || hour > 23) {
                return 0;
            }

            if (minute < 0 || minute > 59) return 0;

            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime base = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(afterMillis), zone);

            for (int i = 1; i <= 366; i++) {
                LocalDate date = base.toLocalDate().plusDays(i);
                DayOfWeek dow = date.getDayOfWeek();
                if (weekdays && (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)) {
                    continue;
                }

                LocalDateTime target = LocalDateTime.of(date, LocalTime.of(hour, minute));
                long millis = target.atZone(zone).toInstant().toEpochMilli();
                if (millis > afterMillis) {
                    return millis;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return 0;
    }
}
