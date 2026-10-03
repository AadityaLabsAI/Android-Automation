package com.aadityalabs.needle2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public final class TaskScheduler {
    private static final String ACTION_RUN = "com.aadityalabs.needle2.RUN";

    private TaskScheduler() {
    }

    private static PendingIntent pi(Context context, String id) {
        Intent intent = new Intent(context, TaskReceiver.class)
                .setAction(ACTION_RUN)
                .setData(Uri.parse("needle2://task/" + Uri.encode(id)))
                .putExtra("task_id", id);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags);
    }

    public static boolean schedule(Context context, TaskStore.Task task) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null || task == null || !task.enabled || task.triggerAt <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long when = Math.max(task.triggerAt, now + 5000L);
        PendingIntent pendingIntent = pi(context, task.id);

        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent);
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, when, pendingIntent);
            }
            return true;
        } catch (SecurityException e) {
            try {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void cancel(Context context, TaskStore.Task task) {
        if (task == null) {
            return;
        }
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            alarm.cancel(pi(context, task.id));
        }
    }

    public static void rescheduleAll(Context context) {
        long now = System.currentTimeMillis();
        for (TaskStore.Task task : TaskStore.all(context)) {
            if (!task.enabled) continue;

            if (task.triggerAt > now) {
                schedule(context, task);
            } else if (task.repeat != null && !task.repeat.trim().isEmpty()) {
                long next = task.repeat.startsWith("cron:")
                        ? CronParser.next(task.repeat.substring(5), now)
                        : TimeParser.nextRepeat(task.repeat, now);
                if (next > now) {
                    task.triggerAt = next;
                    TaskStore.update(context, task);
                    schedule(context, task);
                } else {
                    task.enabled = false;
                    TaskStore.update(context, task);
                }
            } else {
                task.enabled = false;
                TaskStore.update(context, task);
            }
        }
    }
}
