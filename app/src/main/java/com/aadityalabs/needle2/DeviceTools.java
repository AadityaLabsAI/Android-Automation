package com.aadityalabs.needle2;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.JSONObject;
public final class DeviceTools{
    private DeviceTools(){}
    public static JSONObject status(Context c){
        JSONObject o=new JSONObject();
        try{
            BatteryManager b=(BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);
            PowerManager p=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
            o.put("battery_percent",b==null?-1:b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            o.put("screen_on",p!=null&&p.isInteractive());
            o.put("android_api",Build.VERSION.SDK_INT);
            o.put("device",Build.MANUFACTURER+" "+Build.MODEL);
            o.put("accessibility_enabled",AutomationService.get()!=null);
            o.put("automation_enabled",AppState.automationEnabled(c));
        }catch(Exception ignored){}
        return o;
    }
    public static boolean launch(Context c,String pkg){try{Intent i=c.getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return true;}catch(Throwable t){return false;}}
    public static void openAccessibility(Context c){c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public static void openBattery(Context c){c.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
}
