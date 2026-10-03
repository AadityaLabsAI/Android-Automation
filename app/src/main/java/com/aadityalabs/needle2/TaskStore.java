package com.aadityalabs.needle2;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TaskStore {
    public static final class Task {
        public String id;
        public String title;
        public String command;
        public String repeat;
        public long triggerAt;
        public boolean enabled = true;

        JSONObject json() throws Exception {
            return new JSONObject()
                    .put("id", id)
                    .put("title", title)
                    .put("command", command)
                    .put("repeat", repeat == null ? "" : repeat)
                    .put("triggerAt", triggerAt)
                    .put("enabled", enabled);
        }

        static Task parse(JSONObject object) {
            Task task = new Task();
            task.id = nonEmpty(object.optString("id", ""), UUID.randomUUID().toString().substring(0, 8));
            task.title = nonEmpty(object.optString("title", ""), "Scheduled task");
            task.command = object.optString("command", "");
            task.repeat = object.optString("repeat", "");
            task.triggerAt = object.optLong("triggerAt", 0L);
            task.enabled = object.optBoolean("enabled", true);
            if (task.triggerAt < 0) task.triggerAt = 0;
            return task;
        }

        private static String nonEmpty(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value;
        }
    }

    private static final String PREFS = "needle_tasks";
    private static final String KEY = "tasks";

    private TaskStore() {
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Task> all(Context context) {
        ArrayList<Task> tasks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject object = array.optJSONObject(i);
                    if (object != null) {
                        tasks.add(Task.parse(object));
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
        return tasks;
    }

    private static synchronized void save(Context context, List<Task> tasks) {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            try {
                array.put(task.json());
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    public static synchronized Task add(
            Context context, String title, String command, long when, String repeat) {
        Task task = new Task();
        task.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        task.title = title == null || title.trim().isEmpty() ? "Scheduled task" : title.trim();
        task.command = command == null ? "" : command.trim();
        task.triggerAt = when;
        task.repeat = repeat == null ? "" : repeat.trim();

        List<Task> tasks = all(context);
        tasks.add(task);
        save(context, tasks);
        return task;
    }

    public static synchronized void update(Context context, Task updated) {
        if (updated == null || updated.id == null || updated.id.isEmpty()) {
            return;
        }
        List<Task> tasks = all(context);
        for (int i = 0; i < tasks.size(); i++) {
            if (updated.id.equals(tasks.get(i).id)) {
                tasks.set(i, updated);
                save(context, tasks);
                return;
            }
        }
    }

    public static synchronized boolean remove(Context context, String id, String title) {
        List<Task> tasks = all(context);
        boolean removed = false;

        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            boolean match = (!isEmpty(id) && id.equals(task.id))
                    || (!isEmpty(title) && title.equalsIgnoreCase(task.title));
            if (match) {
                TaskScheduler.cancel(context, task);
                tasks.remove(i);
                removed = true;
            }
        }

        if (removed) {
            save(context, tasks);
        }
        return removed;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
