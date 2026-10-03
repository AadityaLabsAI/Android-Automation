package com.aadityalabs.needle2;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

public class TaskJobService extends JobService {
    private static int jobId(String taskId) {
        return 100000 + (taskId.hashCode() & 0x7fffffff);
    }

    public static boolean enqueue(Context context, String taskId) {
        if (taskId == null || taskId.isEmpty()) return false;

        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;

        PersistableBundle extras = new PersistableBundle();
        extras.putString("task_id", taskId);

        JobInfo info = new JobInfo.Builder(
                jobId(taskId),
                new ComponentName(context, TaskJobService.class))
                .setExtras(extras)
                .setOverrideDeadline(0L)
                .build();

        return scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS;
    }

    public static void cancel(Context context, String taskId) {
        if (taskId == null || taskId.isEmpty()) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(jobId(taskId));
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        String id = "";
        if (params.getExtras() != null) {
            id = params.getExtras().getString("task_id", "");
        }

        TaskStore.Task task = findTask(id);
        if (task == null || !task.enabled || task.command.trim().isEmpty()) {
            jobFinished(params, false);
            return false;
        }

        final String taskId = id;
        NeedleEngine.get(getApplicationContext()).run(task.command, new NeedleEngine.Callback() {
            @Override
            public void onProgress(String text) {
            }

            @Override
            public void onDone(String text, boolean ok) {
                try {
                    finishTask(getApplicationContext(), taskId, text, ok);
                } finally {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    private TaskStore.Task findTask(String id) {
        for (TaskStore.Task task : TaskStore.all(this)) {
            if (task.id.equals(id)) return task;
        }
        return null;
    }

    static void finishTask(Context context, String id, String text, boolean ok) {
        TaskStore.Task current = null;
        for (TaskStore.Task task : TaskStore.all(context)) {
            if (task.id.equals(id)) {
                current = task;
                break;
            }
        }
        if (current == null) return;

        String message = text == null ? (ok ? "Task completed." : "Task failed.") : text;
        NotificationHelper.notify(
                context,
                current.title,
                ok ? message : "Task failed: " + message,
                (int) (System.currentTimeMillis() & 0x7fffffff));

        if (!current.enabled) return;

        long now = System.currentTimeMillis();
        String repeat = current.repeat == null ? "" : current.repeat.trim();
        if (repeat.isEmpty()) {
            current.enabled = false;
            TaskStore.update(context, current);
            return;
        }

        long reference = current.triggerAt > 0 ? current.triggerAt : now;
        long next = repeat.startsWith("cron:")
                ? CronParser.next(repeat.substring(5), reference)
                : TimeParser.nextRepeat(repeat, reference);

        for (int i = 0; i < 1000 && next > 0 && next <= now; i++) {
            next = repeat.startsWith("cron:")
                    ? CronParser.next(repeat.substring(5), next)
                    : TimeParser.nextRepeat(repeat, next);
        }

        if (next > now) {
            current.triggerAt = next;
            TaskStore.update(context, current);
            TaskScheduler.schedule(context, current);
        } else {
            current.enabled = false;
            TaskStore.update(context, current);
            NotificationHelper.notify(
                    context,
                    current.title,
                    "Repeating schedule could not determine a safe next run.",
                    (int) (System.currentTimeMillis() & 0x7fffffff));
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
