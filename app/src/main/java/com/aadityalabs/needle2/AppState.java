package com.aadityalabs.needle2;
import android.content.Context;
public final class AppState {
    private static final String PREFS="needle_state";
    private AppState(){}
    public static boolean automationEnabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("automation_enabled",false);}
    public static void setAutomationEnabled(Context c,boolean on){c.getSharedPreferences(PREFS,0).edit().putBoolean("automation_enabled",on).apply();}
}
