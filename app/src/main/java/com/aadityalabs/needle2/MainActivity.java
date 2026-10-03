package com.aadityalabs.needle2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int P = 16;
    private static final int NOTIFICATION_REQUEST = 2001;
    private LinearLayout root, content;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private NeedleEngine engine;
    private final int ink = Color.rgb(17,24,39);
    private final int blue = Color.rgb(37,99,235);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        engine = NeedleEngine.get(this);
        NotificationHelper.ensureChannel(this);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }

        showChat();
    }

    private void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,247,248));

        LinearLayout top = new LinearLayout(this);
        top.setPadding(P,8,P,8);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.WHITE);

        TextView title = label("needle2",22,ink,true);
        top.addView(title,new LinearLayout.LayoutParams(0,52,1));

        status = label("LOCAL",11,Color.rgb(2,122,72),true);
        status.setGravity(Gravity.CENTER);
        top.addView(status,new LinearLayout.LayoutParams(100,36));
        root.addView(top);

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(P,4,P,4);
        nav.addView(navItem("Chat",v->showChat()));
        nav.addView(navItem("Automations",v->showAutomations()));
        nav.addView(navItem("Device",v->showDevice()));
        root.addView(nav);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-1));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);
    }

    private TextView label(String text,float size,int color,boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(null,bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return v;
    }

    private TextView navItem(String text,View.OnClickListener listener) {
        TextView v = label(text,14,ink,true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(8,10,8,10);
        v.setOnClickListener(listener);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        return v;
    }

    private TextView card(String text) {
        TextView v = label(text,14,ink,false);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(P,P,P,P);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(P,6,P,6);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView bubble(String role,String text) {
        TextView v = label(text,14,ink,false);
        v.setPadding(P,12,P,12);
        v.setBackgroundColor("user".equals(role) ? Color.rgb(229,231,235) : Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(P,4,P,4);
        v.setLayoutParams(lp);
        return v;
    }

    private void showChat() {
        base();
        content.addView(card("Local assistant\n\nTell needle2 what to do on this phone. Examples:\nopen an app and tap a button\nread the current screen\nschedule a task for tomorrow\nnotify me in 20 minutes\n\nNo network permission is requested by the app."));

        for (ChatStore.Message m : ChatStore.read(this))
            content.addView(bubble(m.role,m.text));

        LinearLayout composer = new LinearLayout(this);
        composer.setPadding(P,8,P,8);
        composer.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(this);
        input.setHint("Command needle2…");
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(16,12,16,12);
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));

        TextView send = label("Send",14,Color.WHITE,true);
        send.setGravity(Gravity.CENTER);
        send.setBackgroundColor(blue);
        send.setPadding(18,12,18,12);
        send.setOnClickListener(v -> sendCommand());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2,-2);
        sp.setMargins(8,0,0,0);
        composer.addView(send,sp);

        root.addView(composer);
        refreshStatus();
    }

    private void sendCommand() {
        String command = input.getText().toString().trim();
        if (command.isEmpty()) return;

        input.setText("");
        ChatStore.add(this,"user",command);
        content.addView(bubble("user",command));

        TextView result = bubble("assistant","Needle is working locally…");
        content.addView(result);
        scroll.postDelayed(() -> scroll.fullScroll(View.FOCUS_DOWN),50);

        engine.run(command,new NeedleEngine.Callback() {
            @Override public void onProgress(String text) {
                result.setText(text);
            }
            @Override public void onDone(String text,boolean ok) {
                result.setText(text);
                ChatStore.add(MainActivity.this,"assistant",text);
                scroll.postDelayed(() -> scroll.fullScroll(View.FOCUS_DOWN),50);
                refreshStatus();
            }
        });
    }

    private void showAutomations() {
        base();
        content.addView(card("Local scheduled tasks. Long-press a task to cancel it."));
        List<TaskStore.Task> tasks = TaskStore.all(this);

        if (tasks.isEmpty()) {
            content.addView(card("No scheduled tasks yet. Try: “remind me tomorrow at 8 to notify me”."));
        } else {
            for (TaskStore.Task task : tasks) {
                String when = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                        .format(new Date(task.triggerAt));
                TextView row = card(task.title + "\nID: " + task.id + "\nNext: " + when +
                        "\nRepeat: " + (task.repeat.isEmpty() ? "once" : task.repeat));
                row.setOnLongClickListener(v -> {
                    TaskScheduler.cancel(this,task);
                    TaskStore.remove(this,task.id,"");
                    showAutomations();
                    return true;
                });
                content.addView(row);
            }
        }
        refreshStatus();
    }

    private void showDevice() {
        base();
        content.addView(card("Device\n" + DeviceTools.status(this)));

        CheckBox enable = new CheckBox(this);
        enable.setText("Enable automation actions");
        enable.setTextSize(15);
        enable.setTextColor(ink);
        enable.setChecked(AppState.automationEnabled(this));
        enable.setOnCheckedChangeListener((button,isChecked) -> {
            AppState.setAutomationEnabled(this,isChecked);
            refreshStatus();
        });
        content.addView(enable);

        TextView accessibility = card("Accessibility\nRequired for screen reading, taps, typing, scroll and gestures.");
        accessibility.setOnClickListener(v -> DeviceTools.openAccessibility(this));
        content.addView(accessibility);

        TextView battery = card("Background reliability\nOpen battery settings if scheduled tasks are being delayed by OEM restrictions.");
        battery.setOnClickListener(v -> DeviceTools.openBattery(this));
        content.addView(battery);

        if (Build.VERSION.SDK_INT >= 31) {
            boolean exactAllowed = ((android.app.AlarmManager) getSystemService(ALARM_SERVICE))
                    .canScheduleExactAlarms();
            TextView exact = card("Precise schedules\n"
                    + (exactAllowed
                    ? "Exact alarm access is enabled."
                    : "Exact alarm access is disabled; background schedules may use a less precise fallback."));
            exact.setOnClickListener(v -> DeviceTools.openExactAlarm(this));
            content.addView(exact);
        }

        content.addView(card("Privacy\nNo INTERNET permission. Local model, tasks and chat state stay on the phone."));
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean ready = AutomationService.get() != null;
        status.setText(ready ? "LOCAL • READY" : "LOCAL • A11Y OFF");
        status.setTextColor(ready ? Color.rgb(2,122,72) : Color.rgb(180,83,9));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }
}
