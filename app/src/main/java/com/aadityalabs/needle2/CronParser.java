package com.aadityalabs.needle2;

import java.time.*;
import java.util.*;

public final class CronParser {
    private CronParser() {}

    public static long next(String expression, long after) {
        if (expression == null) return 0;
        String[] p = expression.trim().split("\\s+");
        if (p.length != 5) return 0;

        try {
            F minute = new F(p[0], 0, 59);
            F hour = new F(p[1], 0, 23);
            F dayOfMonth = new F(p[2], 1, 31);
            F month = new F(p[3], 1, 12);
            F dayOfWeek = new F(p[4], 0, 7);

            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime current = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(after), zone)
                    .withSecond(0).withNano(0).plusMinutes(1);

            // Search up to one full leap year plus one minute.
            for (int i = 0; i < 527041; i++, current = current.plusMinutes(1)) {
                int dow = current.getDayOfWeek().getValue() % 7;
                boolean domMatch = dayOfMonth.has(current.getDayOfMonth());
                boolean dowMatch = dayOfWeek.has(dow) || (dow == 0 && dayOfWeek.has(7));

                // Standard cron semantics: when both DOM and DOW are restricted,
                // either field may match. If one is '*', the other must match.
                boolean dayMatch = dayOfMonth.wild && dayOfWeek.wild
                        || dayOfMonth.wild && dowMatch
                        || dayOfWeek.wild && domMatch
                        || (!dayOfMonth.wild && !dayOfWeek.wild && (domMatch || dowMatch));

                if (minute.has(current.getMinute())
                        && hour.has(current.getHour())
                        && month.has(current.getMonthValue())
                        && dayMatch) {
                    return current.atZone(zone).toInstant().toEpochMilli();
                }
            }
        } catch (RuntimeException ignored) {
            return 0;
        }
        return 0;
    }

    static final class F {
        final Set<Integer> values = new HashSet<>();
        final boolean wild;

        F(String expression, int low, int high) {
            if (expression == null || expression.trim().isEmpty()) {
                throw new IllegalArgumentException("empty cron field");
            }
            wild = expression.trim().equals("*");

            for (String raw : expression.split(",")) {
                String token = raw.trim();
                if (token.isEmpty()) throw new IllegalArgumentException("empty cron token");

                int step = 1;
                String base = token;
                int slash = token.indexOf('/');
                if (slash >= 0) {
                    if (token.indexOf('/', slash + 1) >= 0) throw new IllegalArgumentException("bad step");
                    base = token.substring(0, slash);
                    step = Integer.parseInt(token.substring(slash + 1));
                    if (step <= 0) throw new IllegalArgumentException("bad step");
                }

                int start;
                int end;
                if (base.equals("*") || base.isEmpty()) {
                    start = low;
                    end = high;
                } else if (base.contains("-")) {
                    String[] range = base.split("-", -1);
                    if (range.length != 2) throw new IllegalArgumentException("bad range");
                    start = Integer.parseInt(range[0]);
                    end = Integer.parseInt(range[1]);
                    if (start > end) throw new IllegalArgumentException("reversed range");
                } else {
                    start = Integer.parseInt(base);
                    end = start;
                    // A stepped single value is allowed only as a no-op.
                    if (slash >= 0 && start < low) throw new IllegalArgumentException("out of range");
                }

                if (start < low || end > high) throw new IllegalArgumentException("out of range");
                for (int value = start; value <= end; value += step) values.add(value);
            }

            if (values.isEmpty()) throw new IllegalArgumentException("empty cron field");
        }

        boolean has(int value) {
            return values.contains(value);
        }
    }
}
