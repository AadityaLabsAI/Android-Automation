package com.aadityalabs.needle2;
import android.content.*;
import android.os.Build;
public class TaskReceiver extends BroadcastReceiver{
    @Override public void onReceive(Context c,Intent i){
        if(!"com.aadityalabs.needle2.RUN".equals(i.getAction()))return;
        Intent s=new Intent(c,TaskExecutionService.class).putExtra("task_id",i.getStringExtra("task_id"));
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
    }
}
