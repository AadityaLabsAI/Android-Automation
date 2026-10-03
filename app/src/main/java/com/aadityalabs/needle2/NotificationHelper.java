package com.aadityalabs.needle2;
import android.app.*;
import android.content.*;
import android.os.Build;
public final class NotificationHelper {
    public static final String CHANNEL="needle_tasks";
    private NotificationHelper(){}
    public static void ensureChannel(Context c){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager m=c.getSystemService(NotificationManager.class);
            if(m!=null)m.createNotificationChannel(new NotificationChannel(CHANNEL,"needle2 tasks",NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
    public static void notify(Context c,String title,String body,int id){
        ensureChannel(c);
        PendingIntent pi=PendingIntent.getActivity(c,id,new Intent(c,MainActivity.class),Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_needle).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true);
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(m!=null)m.notify(id,b.build());
    }
}
