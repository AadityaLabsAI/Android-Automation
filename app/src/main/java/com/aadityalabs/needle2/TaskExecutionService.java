package com.aadityalabs.needle2;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class TaskExecutionService extends Service{
    @Override public void onCreate(){
        super.onCreate();NotificationHelper.ensureChannel(this);
        android.app.Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26
                ?new android.app.Notification.Builder(this,NotificationHelper.CHANNEL)
                :new android.app.Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_needle).setContentTitle("needle2")
                .setContentText("Running scheduled automation").setOngoing(true);
        startForeground(9001,b.build());
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        String id=i==null?"":i.getStringExtra("task_id");
        NeedleEngine.get(getApplicationContext()).run(findCommand(id),new NeedleEngine.Callback(){
            @Override public void onProgress(String t){}
            @Override public void onDone(String text,boolean ok){finishTask(id,text,ok);stopSelfResult(startId);}
        });
        return START_NOT_STICKY;
    }
    private String findCommand(String id){
        for(TaskStore.Task t:TaskStore.all(this))if(t.id.equals(id)&&t.enabled)return t.command;
        return "";
    }
    private void finishTask(String id,String text,boolean ok){
        TaskStore.Task current=null;
        for(TaskStore.Task t:TaskStore.all(this))if(t.id.equals(id)){current=t;break;}
        if(current==null)return;
        NotificationHelper.notify(this,current.title,ok?text:"Task failed: "+text,(int)(System.currentTimeMillis()&0x7fffffff));
        if(!current.enabled)return;
        long next=current.repeat.startsWith("cron:")
                ?CronParser.next(current.repeat.substring(5),System.currentTimeMillis())
                :TimeParser.nextRepeat(current.repeat,System.currentTimeMillis());
        if(next>0){
            current.triggerAt=next;TaskStore.update(this,current);TaskScheduler.schedule(this,current);
        }else if(current.repeat.isEmpty()){
            current.enabled=false;TaskStore.update(this,current);
        }
    }
    @Override public IBinder onBind(Intent i){return null;}
}
