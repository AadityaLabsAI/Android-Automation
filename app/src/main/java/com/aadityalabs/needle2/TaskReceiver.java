package com.aadityalabs.needle2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class TaskReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.aadityalabs.needle2.RUN".equals(intent.getAction())) {
            return;
        }

        String taskId = intent.getStringExtra("task_id");
        if (taskId == null || taskId.isEmpty()) return;

        Intent service = new Intent(context, TaskExecutionService.class)
                .putExtra("task_id", taskId);

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                context.startForegroundService(service);
                return;
            } catch (RuntimeException ignored) {
                // On newer Android versions an inexact/background trigger can still
                // be denied a foreground-service start. Fall back to JobScheduler.
            }
        } else {
            try {
                context.startService(service);
                return;
            } catch (RuntimeException ignored) {
                // Fall through to JobScheduler.
            }
        }

        TaskJobService.enqueue(context, taskId);
    }
}
