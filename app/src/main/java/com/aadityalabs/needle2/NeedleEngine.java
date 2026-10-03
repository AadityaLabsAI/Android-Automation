package com.aadityalabs.needle2;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class NeedleEngine{
    public interface Callback{void onProgress(String text);void onDone(String text,boolean ok);}
    private final Context c;
    private final ExecutorService ex=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean ready=false;
    public NeedleEngine(Context c){this.c=c.getApplicationContext();}
    public void run(String cmd,Callback cb){
        ex.execute(()->{
            try{
                init();
                main.post(()->cb.onProgress("Needle is working locally…"));
                String out=loop(cmd,cb);
                main.post(()->cb.onDone(out,!out.startsWith("Error:")));
            }catch(Throwable t){main.post(()->cb.onDone("Error: "+safe(t.getMessage()),false));}
        });
    }
    private synchronized void init()throws Exception{
        if(ready)return;
        File m=ModelRepository.ensure(c);
        int l=NativeNeedle.nativeLoadModel(m.getAbsolutePath());
        if(l<0)throw new IllegalStateException("model load "+l);
        String tools=asset("tools.json");
        String facts="date: "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.US))+"; locale: "+Locale.getDefault()+"; device: phone; assistant: needle2";
        int i=NativeNeedle.nativeInit(facts,tools,new File(c.getFilesDir(),"tools.idx").getAbsolutePath());
        if(i<0)throw new IllegalStateException("engine init "+i);
        ready=true;
    }
    private String asset(String n)throws Exception{try(InputStream in=c.getAssets().open(n)){byte[] b=new byte[in.available()];int x=in.read(b);return new String(b,0,x,StandardCharsets.UTF_8);}}
    private String loop(String first,Callback cb)throws Exception{
        String turn=first,last="";
        for(int s=0;s<8;s++){
            JSONObject response=new JSONObject(NativeNeedle.nativeComplete(turn,160));
            if("error".equals(response.optString("type")))return "Error: "+response;
            JSONArray calls=response.optJSONArray("function_calls");
            if(calls==null||calls.length()==0)return last.isEmpty()?"No matching local action was selected.":last;
            JSONObject call=calls.getJSONObject(0);
            String name=call.optString("name");
            JSONObject args=call.optJSONObject("arguments");
            main.post(()->cb.onProgress("Running "+name+"…"));
            JSONObject result=exec(name,args==null?new JSONObject():args);
            last=summary(name,result);
            turn=result.toString();
        }
        return last.isEmpty()?"Stopped after 8 automation steps.":last;
    }
    private JSONObject exec(String n,JSONObject a){
        try{
            boolean ro=n.equals("read_screen")||n.equals("get_device_status")||n.equals("list_tasks");
            if(!ro&&!AppState.automationEnabled(c))return new JSONObject().put("ok",false).put("error","automation actions are disabled");
            AutomationService s=AutomationService.get();
            switch(n){
                case "read_screen":return new JSONObject(s==null?"{\\"ok\\":false,\\"error\\":\\"accessibility unavailable\\"}":s.dumpScreen());
                case "click_text":return new JSONObject().put("ok",s!=null&&s.clickText(a.optString("text")));
                case "click_xy":return new JSONObject().put("ok",s!=null&&s.tap(a.optInt("x"),a.optInt("y"),120));
                case "long_click":return new JSONObject().put("ok",s!=null&&s.longClick(a.optString("text"),a.optLong("duration_ms",600)));
                case "type_text":return new JSONObject().put("ok",s!=null&&s.typeText(a.optString("target",""),a.optString("text")));
                case "scroll":{int p=Math.max(1,Math.min(4,a.optInt("pages",1)));boolean ok=s!=null;for(int i=0;i<p&&ok;i++)ok=s.scroll(a.optString("direction","down"));return new JSONObject().put("ok",ok);}
                case "swipe":return new JSONObject().put("ok",s!=null&&s.swipe(a.optInt("x1"),a.optInt("y1"),a.optInt("x2"),a.optInt("y2"),a.optLong("duration_ms",500)));
                case "press_back":return new JSONObject().put("ok",s!=null&&s.back());
                case "go_home":return new JSONObject().put("ok",s!=null&&s.home());
                case "open_app":return new JSONObject().put("ok",DeviceTools.launch(c,a.optString("package")));
                case "wait":Thread.sleep(Math.max(100,Math.min(10000,a.optLong("milliseconds",500))));return new JSONObject().put("ok",true);
                case "get_device_status":return DeviceTools.status(c).put("ok",true);
                case "send_notification":NotificationHelper.notify(c,a.optString("title","needle2"),a.optString("body",""),(int)(System.currentTimeMillis()&0x7fffffff));return new JSONObject().put("ok",true);
                case "schedule_task":{long w=TimeParser.parseWhen(a.optString("when"),System.currentTimeMillis());TaskStore.Task t=TaskStore.add(c,"Scheduled task",a.optString("command"),w,a.optString("repeat",""));TaskScheduler.schedule(c,t);return new JSONObject().put("ok",true).put("task_id",t.id).put("trigger_at",t.triggerAt);}
                case "schedule_cron":{String cron=a.optString("cron");long f=CronParser.next(cron,System.currentTimeMillis());if(f<=0)return new JSONObject().put("ok",false).put("error","invalid cron");TaskStore.Task t=TaskStore.add(c,a.optString("title","Cron task"),a.optString("command"),f,"cron:"+cron);TaskScheduler.schedule(c,t);return new JSONObject().put("ok",true).put("task_id",t.id).put("next",f);}
                case "list_tasks":{JSONArray a2=new JSONArray();for(TaskStore.Task t:TaskStore.all(c))a2.put(new JSONObject().put("id",t.id).put("title",t.title).put("command",t.command).put("trigger_at",t.triggerAt).put("repeat",t.repeat).put("enabled",t.enabled));return new JSONObject().put("ok",true).put("tasks",a2);}
                case "cancel_task":return new JSONObject().put("ok",TaskStore.remove(c,a.optString("id",""),a.optString("title","")));
                default:return new JSONObject().put("ok",false).put("error","unknown tool");
            }
        }catch(Throwable t){try{return new JSONObject().put("ok",false).put("error",safe(t.getMessage()));}catch(Exception ignored){return new JSONObject();}}
    }
    private String summary(String n,JSONObject r){
        if(!r.optBoolean("ok",false))return "Error: "+n+" — "+r.optString("error","failed");
        if(n.startsWith("schedule_"))return "Scheduled successfully. Task ID: "+r.optString("task_id");
        if(n.equals("read_screen"))return "Screen state captured. Continue with the requested action.";
        if(n.equals("list_tasks"))return "Scheduled tasks: "+r.optJSONArray("tasks");
        if(n.equals("get_device_status"))return "Device status: "+r;
        return "Done: "+n.replace('_',' ');
    }
    private static String safe(String s){if(s==null||s.isEmpty())return "unknown error";return s.length()>220?s.substring(0,220):s;}
    public void shutdown(){ex.shutdownNow();}
}
