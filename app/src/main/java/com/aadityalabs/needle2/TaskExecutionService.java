package com.aadityalabs.needle2;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class TaskExecutionService extends Service {
    private static final int FOREGROUND_ID = 9001;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannel(this);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, NotificationHelper.CHANNEL)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_needle)
                .setContentTitle("needle2")
                .setContentText("Running scheduled automation")
                .setOngoing(true);

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String id = intent == null ? "" : intent.getStringExtra("task_id");
        final TaskStore.Task task = findTask(id);

        if (task == null || !task.enabled || task.command.trim().isEmpty()) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        NeedleEngine.get(getApplicationContext()).run(task.command, new NeedleEngine.Callback() {
            @Override
            public void onProgress(String text) {
            }

            @Override
            public void onDone(String text, boolean ok) {
                try {
                    TaskJobService.finishTask(getApplicationContext(), id, text, ok);
                } finally {
                    stopSelfResult(startId);
                }
            }
        });

        return START_NOT_STICKY;
    }

    private TaskStore.Task findTask(String id) {
        if (id == null || id.isEmpty()) return null;
        for (TaskStore.Task task : TaskStore.all(this)) {
            if (task.id.equals(id)) return task;
        }
        return null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
