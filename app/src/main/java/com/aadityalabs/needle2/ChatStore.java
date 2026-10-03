package com.aadityalabs.needle2;
import android.content.*;
import org.json.*;
import java.util.*;
public final class ChatStore{
    public static final class Message{public String role,text;Message(String r,String t){role=r;text=t;}}
    private static final String PREFS="needle_chat",KEY="messages";
    private ChatStore(){}
    public static synchronized List<Message> read(Context c){ArrayList<Message> l=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);l.add(new Message(o.optString("role"),o.optString("text")));}}catch(Exception ignored){}return l;}
    public static synchronized void add(Context c,String r,String t){List<Message> l=read(c);l.add(new Message(r,t));while(l.size()>80)l.remove(0);JSONArray a=new JSONArray();for(Message m:l)try{a.put(new JSONObject().put("role",m.role).put("text",m.text));}catch(Exception ignored){}c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
}
