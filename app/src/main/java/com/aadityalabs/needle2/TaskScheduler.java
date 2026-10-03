package com.aadityalabs.needle2;
import android.app.*;
import android.content.*;
import android.os.Build;
public final class TaskScheduler{
    private TaskScheduler(){}
    private static PendingIntent pi(Context c,String id){Intent i=new Intent(c,TaskReceiver.class).setAction("com.aadityalabs.needle2.RUN").putExtra("task_id",id);int f=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)f|=PendingIntent.FLAG_IMMUTABLE;return PendingIntent.getBroadcast(c,id.hashCode(),i,f);}
    public static void schedule(Context c,TaskStore.Task t){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a==null)return;
        long when=Math.max(t.triggerAt,System.currentTimeMillis()+5000);PendingIntent p=pi(c,t.id);
        try{
            if(Build.VERSION.SDK_INT>=31 && !a.canScheduleExactAlarms()){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);}
            else if(Build.VERSION.SDK_INT>=23)a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);
            else a.setExact(AlarmManager.RTC_WAKEUP,when,p);
        }catch(SecurityException e){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);}
    }
    public static void cancel(Context c,TaskStore.Task t){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a!=null)a.cancel(pi(c,t.id));}
    public static void rescheduleAll(Context c){for(TaskStore.Task t:TaskStore.all(c))if(t.enabled&&t.triggerAt>0)schedule(c,t);}
}
