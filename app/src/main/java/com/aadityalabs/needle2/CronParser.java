package com.aadityalabs.needle2;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class CronParser {
    private CronParser() {
    }

    public static long next(String expression, long afterMillis) {
        if (expression == null || expression.trim().isEmpty()) return 0;

        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) return 0;

        try {
            Field minute = new Field(fields[0], 0, 59);
            Field hour = new Field(fields[1], 0, 23);
            Field dayOfMonth = new Field(fields[2], 1, 31);
            Field month = new Field(fields[3], 1, 12);
            Field dayOfWeek = new Field(fields[4], 0, 7);

            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime current = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(afterMillis), zone)
                    .withSecond(0).withNano(0).plusMinutes(1);

            final int maxMinutes = 1_052_641;
            for (int i = 0; i < maxMinutes; i++, current = current.plusMinutes(1)) {
                if (!month.has(current.getMonthValue())
                        || !minute.has(current.getMinute())
                        || !hour.has(current.getHour())) continue;

                int dow = current.getDayOfWeek().getValue() % 7;
                boolean domMatch = dayOfMonth.has(current.getDayOfMonth());
                boolean dowMatch = dayOfWeek.has(dow) || (dow == 0 && dayOfWeek.has(7));

                boolean dayMatch;
                if (dayOfMonth.wild && dayOfWeek.wild) {
                    dayMatch = true;
                } else if (dayOfMonth.wild) {
                    dayMatch = dowMatch;
                } else if (dayOfWeek.wild) {
                    dayMatch = domMatch;
                } else {
                    dayMatch = domMatch || dowMatch;
                }

                if (dayMatch) {
                    return current.atZone(zone).toInstant().toEpochMilli();
                }
            }
        } catch (RuntimeException ignored) {
        }

        return 0;
    }

    static final class Field {
        final Set<Integer> values = new HashSet<>();
        final boolean wild;

        Field(String expression, int low, int high) {
            if (expression == null || expression.trim().isEmpty()) {
                throw new IllegalArgumentException("empty cron field");
            }

            String normalized = expression.trim();
            this.wild = normalized.equals("*");

            for (String raw : normalized.split(",")) {
                String token = raw.trim();
                if (token.isEmpty()) throw new IllegalArgumentException("empty cron token");
                parseToken(token, low, high);
            }

            if (values.isEmpty()) throw new IllegalArgumentException("empty cron field");
        }

        private void parseToken(String token, int low, int high) {
            String[] stepParts = token.split("/", -1);
            if (stepParts.length > 2) throw new IllegalArgumentException("bad cron step");

            String base = stepParts[0];
            int step = 1;
            if (stepParts.length == 2) {
                if (stepParts[1].isEmpty()) throw new IllegalArgumentException("missing cron step");
                step = Integer.parseInt(stepParts[1]);
                if (step <= 0) throw new IllegalArgumentException("invalid cron step");
            }

            int start;
            int end;
            if ("*".equals(base)) {
                start = low;
                end = high;
            } else if (base.contains("-")) {
                String[] range = base.split("-", -1);
                if (range.length != 2 || range[0].isEmpty() || range[1].isEmpty()) {
                    throw new IllegalArgumentException("bad cron range");
                }
                start = Integer.parseInt(range[0]);
                end = Integer.parseInt(range[1]);
                if (start > end) throw new IllegalArgumentException("reversed cron range");
            } else {
                start = Integer.parseInt(base);
                end = stepParts.length == 2 ? high : start;
            }

            if (start < low || end > high) throw new IllegalArgumentException("cron field out of range");

            for (int value = start; value <= end; value += step) {
                values.add(value);
                if (value > high - step) break;
            }
        }

        boolean has(int value) {
            return values.contains(value);
        }
    }
}
