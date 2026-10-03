package com.aadityalabs.needle2;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity{
    private static final int P=16;
    private LinearLayout root,content;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private NeedleEngine engine;
    private final int ink=Color.rgb(17,24,39),blue=Color.rgb(37,99,235);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Window w=getWindow();
        w.setStatusBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        engine=new NeedleEngine(this);
        NotificationHelper.ensureChannel(this);
        showChat();
    }

    private void base(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,247,248));

        LinearLayout top=new LinearLayout(this);
        top.setPadding(P,8,P,8);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.WHITE);
        TextView title=label("needle2",22,ink,true);
        top.addView(title,new LinearLayout.LayoutParams(0,52,1));
        status=label("LOCAL",11,Color.rgb(2,122,72),true);
        status.setGravity(Gravity.CENTER);
        top.addView(status,new LinearLayout.LayoutParams(100,36));
        root.addView(top);

        LinearLayout nav=new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(P,4,P,4);
        nav.addView(nav("Chat",v->showChat()));
        nav.addView(nav("Automations",v->showAutomations()));
        nav.addView(nav("Device",v->showDevice()));
        root.addView(nav);

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-1));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private TextView label(String t,float s,int c,boolean bold){
        TextView v=new TextView(this);
        v.setText(t);v.setTextSize(s);v.setTextColor(c);
        v.setTypeface(null,bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
        return v;
    }
    private TextView nav(String t,View.OnClickListener l){
        TextView v=label(t,14,ink,true);
        v.setGravity(Gravity.CENTER);v.setPadding(8,10,8,10);v.setOnClickListener(l);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));return v;
    }
    private TextView card(String t){
        TextView v=label(t,14,ink,false);v.setBackgroundColor(Color.WHITE);v.setPadding(P,P,P,P);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(P,6,P,6);v.setLayoutParams(lp);return v;
    }
    private TextView bubble(String role,String text){
        TextView v=label(text,14,ink,false);v.setPadding(P,12,P,12);
        v.setBackgroundColor("user".equals(role)?Color.rgb(229,231,235):Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(P,4,P,4);v.setLayoutParams(lp);return v;
    }

    private void showChat(){
        base();
        content.addView(card("Local assistant\\n\\nTell needle2 what to do on this phone. Examples:\\nopen an app and tap a button\\nread the current screen\\nschedule a task for tomorrow\\nnotify me in 20 minutes\\n\\nNo network permission is requested by the app."));
        for(ChatStore.Message m:ChatStore.read(this))content.addView(bubble(m.role,m.text));

        LinearLayout comp=new LinearLayout(this);
        comp.setPadding(P,8,P,8);
        comp.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this);
        input.setHint("Command needle2…");
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(16,12,16,12);
        comp.addView(input,new LinearLayout.LayoutParams(0,-2,1));

        TextView send=label("Send",14,Color.WHITE,true);
        send.setGravity(Gravity.CENTER);
        send.setBackgroundColor(blue);
        send.setPadding(18,12,18,12);
        send.setOnClickListener(v->send());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMargins(8,0,0,0);
        comp.addView(send,sp);
        root.addView(comp);
        refresh();
    }

    private void send(){
        String cmd=input.getText().toString().trim();
        if(cmd.isEmpty())return;
        input.setText("");
        ChatStore.add(this,"user",cmd);
        content.addView(bubble("user",cmd));
        TextView result=bubble("assistant","Needle is working locally…");
        content.addView(result);
        scroll.postDelayed(()->scroll.fullScroll(View.FOCUS_DOWN),50);
        engine.run(cmd,new NeedleEngine.Callback(){
            public void onProgress(String t){result.setText(t);}
            public void onDone(String t,boolean ok){result.setText(t);ChatStore.add(MainActivity.this,"assistant",t);scroll.postDelayed(()->scroll.fullScroll(View.FOCUS_DOWN),50);refresh();}
        });
    }

    private void showAutomations(){
        base();
        content.addView(card("Local scheduled tasks. Long-press a task to cancel it."));
        List<TaskStore.Task> tasks=TaskStore.all(this);
        if(tasks.isEmpty())content.addView(card("No scheduled tasks yet. Try: “remind me tomorrow at 8 to notify me”."));
        else for(TaskStore.Task t:tasks){
            String when=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t.triggerAt));
            TextView row=card(t.title+"\\nID: "+t.id+"\\nNext: "+when+"\\nRepeat: "+(t.repeat.isEmpty()?"once":t.repeat));
            row.setOnLongClickListener(v->{TaskScheduler.cancel(this,t);TaskStore.remove(this,t.id,"");showAutomations();return true;});
            content.addView(row);
        }
        refresh();
    }

    private void showDevice(){
        base();
        content.addView(card("Device\\n"+DeviceTools.status(this)));
        CheckBox arm=new CheckBox(this);
        arm.setText("Enable automation actions");
        arm.setTextSize(15);
        arm.setTextColor(ink);
        arm.setChecked(AppState.automationEnabled(this));
        arm.setOnCheckedChangeListener((b,on)->{AppState.setAutomationEnabled(this,on);refresh();});
        content.addView(arm);

        TextView a=card("Accessibility\\nRequired for screen reading, taps, typing, scroll and gestures.");
        a.setOnClickListener(v->DeviceTools.openAccessibility(this));
        content.addView(a);

        TextView bat=card("Background reliability\\nOpen battery settings if scheduled tasks are being delayed by OEM restrictions.");
        bat.setOnClickListener(v->DeviceTools.openBattery(this));
        content.addView(bat);

        content.addView(card("Privacy\\nNo INTERNET permission. Local model, tasks and chat state stay on the phone."));
        refresh();
    }

    private void refresh(){
        if(status==null)return;
        boolean a11y=AutomationService.get()!=null;
        status.setText(a11y?"LOCAL • READY":"LOCAL • A11Y OFF");
        status.setTextColor(a11y?Color.rgb(2,122,72):Color.rgb(180,83,9));
    }

    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){if(engine!=null)engine.shutdown();super.onDestroy();}
}
