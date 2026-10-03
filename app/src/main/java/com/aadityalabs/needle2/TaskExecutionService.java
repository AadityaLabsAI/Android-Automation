package com.aadityalabs.needle2;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskExecutionService extends Service{
    private final ExecutorService ex=Executors.newSingleThreadExecutor();
    @Override public void onCreate(){
        super.onCreate();
        NotificationHelper.ensureChannel(this);
        android.app.Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26
                ?new android.app.Notification.Builder(this,NotificationHelper.CHANNEL)
                :new android.app.Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_needle).setContentTitle("needle2").setContentText("Running scheduled automation").setOngoing(true);
        startForeground(9001,b.build());
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        String id=i==null?"":i.getStringExtra("task_id");
        ex.execute(()->runTask(id,startId));
        return START_NOT_STICKY;
    }
    private void runTask(String id,int startId){
        TaskStore.Task task=null;
        for(TaskStore.Task t:TaskStore.all(this))if(t.id.equals(id)){task=t;break;}
        if(task==null||!task.enabled){stopSelfResult(startId);return;}
        TaskStore.Task cur=task;
        new NeedleEngine(getApplicationContext()).run(cur.command,new NeedleEngine.Callback(){
            public void onProgress(String t){}
            public void onDone(String text,boolean ok){
                NotificationHelper.notify(TaskExecutionService.this,cur.title,ok?text:"Task failed: "+text,(int)(System.currentTimeMillis()&0x7fffffff));
                TaskStore.Task latest=null;
                for(TaskStore.Task t:TaskStore.all(TaskExecutionService.this))if(t.id.equals(cur.id)){latest=t;break;}
                if(latest!=null&&latest.enabled){
                    long next=latest.repeat.startsWith("cron:")?CronParser.next(latest.repeat.substring(5),System.currentTimeMillis()):TimeParser.nextRepeat(latest.repeat,System.currentTimeMillis());
                    if(next>0){latest.triggerAt=next;TaskStore.update(TaskExecutionService.this,latest);TaskScheduler.schedule(TaskExecutionService.this,latest);}
                    else if(latest.repeat.isEmpty()){latest.enabled=false;TaskStore.update(TaskExecutionService.this,latest);}
                }
                stopSelfResult(startId);
            }
        });
    }
    @Override public void onDestroy(){ex.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
