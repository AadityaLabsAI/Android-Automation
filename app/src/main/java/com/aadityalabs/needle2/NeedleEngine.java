package com.aadityalabs.needle2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NeedleEngine {
    public interface Callback {
        void onProgress(String text);
        void onDone(String text, boolean ok);
    }

    private static volatile NeedleEngine instance;

    public static synchronized NeedleEngine get(Context context) {
        if (instance == null) {
            instance = new NeedleEngine(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "needle2-engine");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean ready;

    private NeedleEngine(Context context) {
        this.context = context;
    }

    public void run(String command, Callback callback) {
        if (callback == null) return;

        String input = command == null ? "" : command.trim();
        if (input.isEmpty()) {
            main.post(() -> callback.onDone("Error: empty command", false));
            return;
        }

        executor.execute(() -> {
            try {
                initialize();
                postProgress(callback, "Needle is working locally…");
                String result = executeLoop(input, callback);
                main.post(() -> callback.onDone(result, !result.startsWith("Error:")));
            } catch (Throwable t) {
                main.post(() -> callback.onDone(
                        "Error: " + safe(t.getMessage()), false));
            }
        });
    }

    private synchronized void initialize() throws Exception {
        if (ready) return;

        try {
            try (AssetFileDescriptor model = ModelRepository.openModel(context)) {
                int load = NativeNeedle.nativeLoadModelFd(
                        model.getParcelFileDescriptor().getFd(),
                        model.getStartOffset(),
                        model.getLength());
                if (load < 0) {
                    throw new IllegalStateException("Needle model load failed: " + load);
                }
            }

            String tools = readAsset("tools.json");
            String facts = "date: "
                    + LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
                    + "; locale: " + Locale.getDefault()
                    + "; device: " + android.os.Build.MANUFACTURER + " "
                    + android.os.Build.MODEL
                    + "; android_api: " + android.os.Build.VERSION.SDK_INT
                    + "; assistant: needle2"
                    + "; local_only: true";

            File index = new File(context.getFilesDir(), "tools.idx");
            int init = NativeNeedle.nativeInit(
                    facts, tools, index.getAbsolutePath());
            if (init < 0) {
                throw new IllegalStateException("Needle init failed: " + init);
            }

            ready = true;
        } catch (Throwable t) {
            ready = false;
            try {
                NativeNeedle.nativeReset();
            } catch (Throwable ignored) {
            }
            if (t instanceof Exception) throw (Exception) t;
            throw new IllegalStateException(t);
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String executeLoop(String first, Callback callback) throws Exception {
        String turn = first;
        String last = "";

        for (int step = 0; step < 8; step++) {
            String raw = NativeNeedle.nativeComplete(turn, 160);
            if (raw == null || raw.trim().isEmpty()) {
                return "Error: Needle returned an empty response.";
            }

            JSONObject response = new JSONObject(raw);
            if ("error".equals(response.optString("type"))) {
                return "Error: " + response;
            }

            JSONArray calls = response.optJSONArray("function_calls");
            if (calls == null || calls.length() == 0) {
                return last.isEmpty()
                        ? "No matching local action was selected."
                        : last;
            }

            JSONObject call = calls.optJSONObject(0);
            if (call == null) {
                return "Error: Needle returned an invalid tool call.";
            }

            String name = call.optString("name", "").trim();
            if (name.isEmpty()) {
                return "Error: Needle returned a tool call without a name.";
            }

            JSONObject args = call.optJSONObject("arguments");
            if (args == null) args = new JSONObject();

            boolean readOnly = isReadOnly(name);
            double confidence = response.optDouble("confidence", 0.0);

            if (!readOnly && (!Double.isFinite(confidence) || confidence < 0.72)) {
                return "Error: Needle confidence was below the safety threshold; no action was executed.";
            }

            postProgress(callback, "Running " + name + "…");
            JSONObject result = executeTool(name, args);
            last = summarize(name, result);
            turn = result.toString();

            if (!result.optBoolean("ok", false) && !"read_screen".equals(name)) {
                return last;
            }
        }

        return last.isEmpty() ? "Stopped after 8 automation steps." : last;
    }

    private JSONObject executeTool(String name, JSONObject args) {
        try {
            if (!isKnownTool(name)) {
                return new JSONObject().put("ok", false).put("error", "unknown tool");
            }

            boolean readOnly = isReadOnly(name);
            if (!readOnly && !AppState.automationEnabled(context)) {
                return new JSONObject()
                        .put("ok", false)
                        .put("error", "automation actions are disabled");
            }

            AutomationService service = AutomationService.get();

            switch (name) {
                case "read_screen":
                    return new JSONObject(service == null
                            ? "{\"ok\":false,\"error\":\"accessibility unavailable\"}"
                            : service.dumpScreen());

                case "click_text":
                    return new JSONObject()
                            .put("ok", service != null
                                    && service.clickText(args.optString("text", "")));

                case "click_xy":
                    return new JSONObject()
                            .put("ok", service != null
                                    && service.tap(args.optInt("x"), args.optInt("y"), 120));

                case "long_click":
                    return new JSONObject()
                            .put("ok", service != null
                                    && service.longClick(
                                    args.optString("text", ""),
                                    args.optLong("duration_ms", 600)));

                case "type_text":
                    return new JSONObject()
                            .put("ok", service != null
                                    && service.typeText(
                                    args.optString("target", ""),
                                    args.optString("text", "")));

                case "scroll": {
                    int pages = Math.max(1, Math.min(4, args.optInt("pages", 1)));
                    boolean ok = service != null;
                    for (int i = 0; i < pages && ok; i++) {
                        ok = service.scroll(args.optString("direction", "down"));
                    }
                    return new JSONObject().put("ok", ok);
                }

                case "swipe":
                    return new JSONObject()
                            .put("ok", service != null
                                    && service.swipe(
                                    args.optInt("x1"), args.optInt("y1"),
                                    args.optInt("x2"), args.optInt("y2"),
                                    args.optLong("duration_ms", 500)));

                case "press_back":
                    return new JSONObject()
                            .put("ok", service != null && service.back());

                case "go_home":
                    return new JSONObject()
                            .put("ok", service != null && service.home());

                case "open_app":
                    return new JSONObject().put(
                            "ok",
                            DeviceTools.launch(context, args.optString("package", "")));

                case "wait":
                    Thread.sleep(Math.max(
                            100L,
                            Math.min(10000L, args.optLong("milliseconds", 500L))));
                    return new JSONObject().put("ok", true);

                case "get_device_status":
                    return DeviceTools.status(context).put("ok", true);

                case "send_notification":
                    NotificationHelper.notify(
                            context,
                            args.optString("title", "needle2"),
                            args.optString("body", ""),
                            (int) (System.currentTimeMillis() & 0x7fffffff));
                    return new JSONObject().put("ok", true);

                case "schedule_task": {
                    String command = args.optString("command", "").trim();
                    if (command.isEmpty()) {
                        return new JSONObject().put("ok", false)
                                .put("error", "scheduled command is empty");
                    }

                    long now = System.currentTimeMillis();
                    long when = TimeParser.parseWhen(args.optString("when", ""), now);
                    if (when <= now) {
                        return new JSONObject().put("ok", false)
                                .put("error", "invalid or past schedule time");
                    }

                    String repeat = args.optString("repeat", "").trim();
                    if (!repeat.isEmpty()) {
                        long check = repeat.startsWith("cron:")
                                ? CronParser.next(repeat.substring(5), when)
                                : TimeParser.nextRepeat(repeat, when);
                        if (check <= when) {
                            return new JSONObject().put("ok", false)
                                    .put("error", "invalid repeat schedule");
                        }
                    }

                    TaskStore.Task task = TaskStore.add(
                            context,
                            "Scheduled task",
                            command,
                            when,
                            repeat);

                    if (!TaskScheduler.schedule(context, task)) {
                        TaskStore.remove(context, task.id, "");
                        return new JSONObject().put("ok", false)
                                .put("error", "could not schedule the task");
                    }

                    return new JSONObject()
                            .put("ok", true)
                            .put("task_id", task.id)
                            .put("trigger_at", task.triggerAt);
                }

                case "schedule_cron": {
                    String cron = args.optString("cron", "").trim();
                    String command = args.optString("command", "").trim();
                    if (cron.isEmpty() || command.isEmpty()) {
                        return new JSONObject().put("ok", false)
                                .put("error", "cron and command are required");
                    }

                    long now = System.currentTimeMillis();
                    long first = CronParser.next(cron, now);
                    if (first <= now) {
                        return new JSONObject().put("ok", false)
                                .put("error", "invalid cron");
                    }

                    TaskStore.Task task = TaskStore.add(
                            context,
                            args.optString("title", "Cron task"),
                            command,
                            first,
                            "cron:" + cron);

                    if (!TaskScheduler.schedule(context, task)) {
                        TaskStore.remove(context, task.id, "");
                        return new JSONObject().put("ok", false)
                                .put("error", "could not schedule the cron task");
                    }

                    return new JSONObject()
                            .put("ok", true)
                            .put("task_id", task.id)
                            .put("next", first);
                }

                case "list_tasks": {
                    JSONArray tasks = new JSONArray();
                    for (TaskStore.Task task : TaskStore.all(context)) {
                        tasks.put(new JSONObject()
                                .put("id", task.id)
                                .put("title", task.title)
                                .put("command", task.command)
                                .put("trigger_at", task.triggerAt)
                                .put("repeat", task.repeat)
                                .put("enabled", task.enabled));
                    }
                    return new JSONObject().put("ok", true).put("tasks", tasks);
                }

                case "cancel_task": {
                    String id = args.optString("id", "").trim();
                    String title = args.optString("title", "").trim();
                    if (id.isEmpty() && title.isEmpty()) {
                        return new JSONObject().put("ok", false)
                                .put("error", "task id or title is required");
                    }
                    return new JSONObject()
                            .put("ok", TaskStore.remove(context, id, title));
                }

                default:
                    return new JSONObject().put("ok", false)
                            .put("error", "unknown tool");
            }
        } catch (Throwable t) {
            try {
                return new JSONObject()
                        .put("ok", false)
                        .put("error", safe(t.getMessage()));
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private static boolean isReadOnly(String name) {
        return "read_screen".equals(name)
                || "get_device_status".equals(name)
                || "list_tasks".equals(name);
    }

    private static boolean isKnownTool(String name) {
        switch (name) {
            case "read_screen":
            case "click_text":
            case "click_xy":
            case "long_click":
            case "type_text":
            case "scroll":
            case "swipe":
            case "press_back":
            case "go_home":
            case "open_app":
            case "wait":
            case "get_device_status":
            case "send_notification":
            case "schedule_task":
            case "schedule_cron":
            case "list_tasks":
            case "cancel_task":
                return true;
            default:
                return false;
        }
    }

    private String summarize(String name, JSONObject result) {
        if (!result.optBoolean("ok", false)) {
            return "Error: " + name + " — "
                    + result.optString("error", "failed");
        }

        if (name.startsWith("schedule_")) {
            return "Scheduled successfully. Task ID: "
                    + result.optString("task_id");
        }
        if ("read_screen".equals(name)) {
            return "Screen state captured. Continue with the requested action.";
        }
        if ("list_tasks".equals(name)) {
            return "Scheduled tasks: " + result.optJSONArray("tasks");
        }
        if ("get_device_status".equals(name)) {
            return "Device status: " + result;
        }
        return "Done: " + name.replace('_', ' ');
    }

    private void postProgress(Callback callback, String text) {
        main.post(() -> callback.onProgress(text));
    }

    private static String safe(String text) {
        if (text == null || text.isEmpty()) return "unknown error";
        return text.length() > 220 ? text.substring(0, 220) : text;
    }
}
