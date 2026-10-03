package com.aadityalabs.needle2;
import android.accessibilityservice.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.accessibility.*;
import java.util.*;
public class AutomationService extends AccessibilityService{
    private static volatile AutomationService instance;
    public static AutomationService get(){return instance;}
    @Override public void onServiceConnected(){instance=this;AccessibilityServiceInfo i=getServiceInfo();if(i!=null){i.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;setServiceInfo(i);}}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    private AccessibilityNodeInfo root(){try{return getRootInActiveWindow();}catch(Throwable t){return null;}}
    public synchronized String dumpScreen(){
        AccessibilityNodeInfo r=root();
        if(r==null)return "{\\"ok\\":false,\\"error\\":\\"accessibility root unavailable\\"}";
        ArrayList<String> out=new ArrayList<>();collect(r,out,0);
        StringBuilder b=new StringBuilder("{\\"ok\\":true,\\"package\\":\\"").append(esc(String.valueOf(r.getPackageName()))).append("\\",\\"nodes\\":[");
        for(int i=0;i<out.size();i++){if(i>0)b.append(',');b.append(out.get(i));}
        return b.append("]}").toString();
    }
    private void collect(AccessibilityNodeInfo n,List<String> out,int depth){
        if(n==null||out.size()>=50||depth>12)return;
        Rect r=new Rect();n.getBoundsInScreen(r);
        String t=n.getText()==null?"":n.getText().toString(),d=n.getContentDescription()==null?"":n.getContentDescription().toString();
        if(t.length()>80)t=t.substring(0,80);if(d.length()>80)d=d.substring(0,80);
        if(!t.isEmpty()||!d.isEmpty()||n.isClickable()||n.isEditable()||n.isScrollable())
            out.add("{\\"text\\":\\"" +esc(t)+"\\",\\"desc\\":\\"" +esc(d)+"\\",\\"clickable\\":"+n.isClickable()+",\\"editable\\":"+n.isEditable()+",\\"scrollable\\":"+n.isScrollable()+",\\"bounds\\":\\"" +r.left+","+r.top+","+r.right+","+r.bottom+"\\"}");
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),out,depth+1);
    }
    private AccessibilityNodeInfo find(String q){AccessibilityNodeInfo r=root();if(r==null)return null;List<AccessibilityNodeInfo> x=r.findAccessibilityNodeInfosByText(q);if(x!=null&&!x.isEmpty())return x.get(0);return rec(r,q.toLowerCase(Locale.US));}
    private AccessibilityNodeInfo rec(AccessibilityNodeInfo n,String q){if(n==null)return null;CharSequence t=n.getText(),d=n.getContentDescription();if((t!=null&&t.toString().toLowerCase(Locale.US).contains(q))||(d!=null&&d.toString().toLowerCase(Locale.US).contains(q)))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=rec(n.getChild(i),q);if(x!=null)return x;}return null;}
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){AccessibilityNodeInfo x=n;for(int i=0;i<4&&x!=null;i++){if(x.isClickable())return x;x=x.getParent();}return n;}
    public boolean clickText(String q){AccessibilityNodeInfo n=find(q);if(n==null)return false;AccessibilityNodeInfo t=clickable(n);if(t.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;Rect r=new Rect();t.getBoundsInScreen(r);return tap((r.left+r.right)/2f,(r.top+r.bottom)/2f,120);}
    public boolean longClick(String q,long dur){AccessibilityNodeInfo n=find(q);if(n==null)return false;if(android.os.Build.VERSION.SDK_INT>=21&&n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,new Bundle()))return true;Rect r=new Rect();n.getBoundsInScreen(r);return tap((r.left+r.right)/2f,(r.top+r.bottom)/2f,dur);}
    public boolean typeText(String target,String value){AccessibilityNodeInfo n=(target==null||target.isEmpty())?firstEditable(root()):find(target);if(n==null)return false;Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}
    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo n){if(n==null)return null;if(n.isEditable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=firstEditable(n.getChild(i));if(x!=null)return x;}return null;}
    public boolean scroll(String direction){AccessibilityNodeInfo n=scrollable(root());if(n==null)return false;return n.performAction("up".equalsIgnoreCase(direction)?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}
    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo n){if(n==null)return null;if(n.isScrollable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo x=scrollable(n.getChild(i));if(x!=null)return x;}return null;}
    public boolean tap(float x,float y,long duration){Path p=new Path();p.moveTo(x,y);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(80,duration))).build();return dispatchGesture(g,null,null);}
    public boolean swipe(float x1,float y1,float x2,float y2,long duration){Path p=new Path();p.moveTo(x1,y1);p.lineTo(x2,y2);GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(100,duration))).build();return dispatchGesture(g,null,null);}
    public boolean back(){return performGlobalAction(GLOBAL_ACTION_BACK);}
    public boolean home(){return performGlobalAction(GLOBAL_ACTION_HOME);}
    private static String esc(String s){return s.replace("\\\\","\\\\\\\\").replace("\"","\\\\\"").replace("\\n"," ").replace("\\r"," ");}
}
