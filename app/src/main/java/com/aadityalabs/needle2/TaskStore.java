package com.aadityalabs.needle2;
import android.content.*;
import org.json.*;
import java.util.*;
public final class TaskStore {
    public static final class Task{
        public String id,title,command,repeat;public long triggerAt;public boolean enabled=true;
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("title",title).put("command",command).put("repeat",repeat==null?"":repeat).put("triggerAt",triggerAt).put("enabled",enabled);}
        static Task parse(JSONObject o){Task t=new Task();t.id=o.optString("id",UUID.randomUUID().toString().substring(0,8));t.title=o.optString("title","Scheduled task");t.command=o.optString("command","");t.repeat=o.optString("repeat","");t.triggerAt=o.optLong("triggerAt",0);t.enabled=o.optBoolean("enabled",true);return t;}
    }
    private static final String PREFS="needle_tasks",KEY="tasks";
    private TaskStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    public static synchronized List<Task> all(Context c){ArrayList<Task> l=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString(KEY,"[]"));for(int i=0;i<a.length();i++)l.add(Task.parse(a.getJSONObject(i)));}catch(Exception ignored){}return l;}
    private static synchronized void save(Context c,List<Task> l){JSONArray a=new JSONArray();for(Task t:l)try{a.put(t.json());}catch(Exception ignored){}prefs(c).edit().putString(KEY,a.toString()).apply();}
    public static synchronized Task add(Context c,String title,String command,long when,String repeat){Task t=new Task();t.id=UUID.randomUUID().toString().substring(0,8);t.title=title==null||title.isEmpty()?"Scheduled task":title;t.command=command;t.triggerAt=when;t.repeat=repeat==null?"":repeat;List<Task> l=all(c);l.add(t);save(c,l);return t;}
    public static synchronized void update(Context c,Task u){List<Task> l=all(c);for(int i=0;i<l.size();i++)if(l.get(i).id.equals(u.id)){l.set(i,u);save(c,l);return;}}
    public static synchronized boolean remove(Context c,String id,String title){List<Task> l=all(c);boolean ok=false;for(int i=l.size()-1;i>=0;i--){Task t=l.get(i);if((!id.isEmpty()&&t.id.equals(id))||(!title.isEmpty()&&t.title.equalsIgnoreCase(title))){l.remove(i);ok=true;}}save(c,l);return ok;}
}
