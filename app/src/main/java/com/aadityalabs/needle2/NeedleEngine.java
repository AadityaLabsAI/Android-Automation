package com.aadityalabs.needle2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
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
        if (instance == null) instance = new NeedleEngine(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean ready;

    private NeedleEngine(Context context) {
        this.context = context;
    }

    public void run(String command, Callback callback) {
        executor.execute(() -> {
            try {
                initialize();
                postProgress(callback, "Needle is working locally…");
                String result = executeLoop(command, callback);
                main.post(() -> callback.onDone(result, !result.startsWith("Error:")));
            } catch (Throwable t) {
                main.post(() -> callback.onDone("Error: " + safe(t.getMessage()), false));
            }
        });
    }

    private synchronized void initialize() throws Exception {
        if (ready) return;

        try (AssetFileDescriptor model = ModelRepository.openModel(context)) {
            int load = NativeNeedle.nativeLoadModelFd(
                    model.getParcelFileDescriptor().getFd(),
                    model.getStartOffset(),
                    model.getLength());
            if (load < 0) throw new IllegalStateException("Needle model load failed: " + load);
        }

        String tools = readAsset("tools.json");
        String facts = "date: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
                + "; locale: " + Locale.getDefault()
                + "; device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "; assistant: needle2";

        File index = new File(context.getFilesDir(), "tools.idx");
        int init = NativeNeedle.nativeInit(facts, tools, index.getAbsolutePath());
        if (init < 0) throw new IllegalStateException("Needle init failed: " + init);
        ready = true;
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            byte[] bytes = new byte[in.available()];
            int n = in.read(bytes);
            return new String(bytes, 0, n, StandardCharsets.UTF_8);
        }
    }

    private String executeLoop(String first, Callback callback) throws Exception {
        String turn = first;
        String last = "";

        for (int step = 0; step < 8; step++) {
            JSONObject response = new JSONObject(NativeNeedle.nativeComplete(turn, 160));
            if ("error".equals(response.optString("type"))) return "Error: " + response;

            JSONArray calls = response.optJSONArray("function_calls");
            if (calls == null || calls.length() == 0) {
                return last.isEmpty() ? "No matching local action was selected." : last;
            }

            JSONObject call = calls.getJSONObject(0);
            String name = call.optString("name");
            JSONObject args = call.optJSONObject("arguments");
            double confidence = response.optDouble("confidence", 1.0);

            boolean readOnly = "read_screen".equals(name)
                    || "get_device_status".equals(name)
                    || "list_tasks".equals(name);

            if (!readOnly && confidence < 0.72) {
                return "Error: Needle confidence was below the safety threshold; no action was executed.";
            }

            postProgress(callback, "Running " + name + "…");
            JSONObject result = executeTool(name, args == null ? new JSONObject() : args);
            last = summarize(name, result);
            turn = result.toString();

            if (!result.optBoolean("ok", false) && !"read_screen".equals(name)) return last;
        }

        return last.isEmpty() ? "Stopped after 8 automation steps." : last;
    }

    private JSONObject executeTool(String name, JSONObject args) {
        try {
            boolean readOnly = "read_screen".equals(name)
                    || "get_device_status".equals(name)
                    || "list_tasks".equals(name);

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
                    return new JSONObject().put("ok", service != null
                            && service.clickText(args.optString("text")));
                case "click_xy":
                    return new JSONObject().put("ok", service != null
                            && service.tap(args.optInt("x"), args.optInt("y"), 120));
                case "long_click":
                    return new JSONObject().put("ok", service != null
                            && service.longClick(args.optString("text"), args.optLong("duration_ms", 600)));
                case "type_text":
                    return new JSONObject().put("ok", service != null
                            && service.typeText(args.optString("target", ""), args.optString("text")));
                case "scroll": {
                    int pages = Math.max(1, Math.min(4, args.optInt("pages", 1)));
                    boolean ok = service != null;
                    for (int i = 0; i < pages && ok; i++) ok = service.scroll(args.optString("direction", "down"));
                    return new JSONObject().put("ok", ok);
                }
                case "swipe":
                    return new JSONObject().put("ok", service != null
                            && service.swipe(args.optInt("x1"), args.optInt("y1"),
                            args.optInt("x2"), args.optInt("y2"), args.optLong("duration_ms", 500)));
                case "press_back":
                    return new JSONObject().put("ok", service != null && service.back());
                case "go_home":
                    return new JSONObject().put("ok", service != null && service.home());
                case "open_app":
                    return new JSONObject().put("ok", DeviceTools.launch(context, args.optString("package")));
                case "wait":
                    Thread.sleep(Math.max(100, Math.min(10000, args.optLong("milliseconds", 500))));
                    return new JSONObject().put("ok", true);
                case "get_device_status":
                    return DeviceTools.status(context).put("ok", true);
                case "send_notification":
                    NotificationHelper.notify(context, args.optString("title", "needle2"),
                            args.optString("body", ""),
                            (int)(System.currentTimeMillis() & 0x7fffffff));
                    return new JSONObject().put("ok", true);
                case "schedule_task": {
                    long when = TimeParser.parseWhen(args.optString("when"), System.currentTimeMillis());
                    if (when <= System.currentTimeMillis()) return new JSONObject().put("ok", false).put("error", "invalid or past schedule time");
                    TaskStore.Task task = TaskStore.add(context, "Scheduled task",
                            args.optString("command"), when, args.optString("repeat", ""));
                    TaskScheduler.schedule(context, task);
                    return new JSONObject().put("ok", true).put("task_id", task.id).put("trigger_at", task.triggerAt);
                }
                case "schedule_cron": {
                    String cron = args.optString("cron");
                    long first = CronParser.next(cron, System.currentTimeMillis());
                    if (first <= 0) return new JSONObject().put("ok", false).put("error", "invalid cron");
                    TaskStore.Task task = TaskStore.add(context, args.optString("title", "Cron task"),
                            args.optString("command"), first, "cron:" + cron);
                    TaskScheduler.schedule(context, task);
                    return new JSONObject().put("ok", true).put("task_id", task.id).put("next", first);
                }
                case "list_tasks": {
                    JSONArray tasks = new JSONArray();
                    for (TaskStore.Task t : TaskStore.all(context))
                        tasks.put(new JSONObject().put("id", t.id).put("title", t.title)
                                .put("command", t.command).put("trigger_at", t.triggerAt)
                                .put("repeat", t.repeat).put("enabled", t.enabled));
                    return new JSONObject().put("ok", true).put("tasks", tasks);
                }
                case "cancel_task":
                    return new JSONObject().put("ok", TaskStore.remove(
                            context, args.optString("id", ""), args.optString("title", "")));
                default:
                    return new JSONObject().put("ok", false).put("error", "unknown tool");
            }
        } catch (Throwable t) {
            try { return new JSONObject().put("ok", false).put("error", safe(t.getMessage())); }
            catch (Exception ignored) { return new JSONObject(); }
        }
    }

    private String summarize(String name, JSONObject result) {
        if (!result.optBoolean("ok", false))
            return "Error: " + name + " — " + result.optString("error", "failed");
        if (name.startsWith("schedule_"))
            return "Scheduled successfully. Task ID: " + result.optString("task_id");
        if ("read_screen".equals(name))
            return "Screen state captured. Continue with the requested action.";
        if ("list_tasks".equals(name))
            return "Scheduled tasks: " + result.optJSONArray("tasks");
        if ("get_device_status".equals(name))
            return "Device status: " + result;
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
