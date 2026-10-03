package com.aadityalabs.needle2;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import org.json.JSONObject;

public final class DeviceTools {
    private DeviceTools() {
    }

    public static JSONObject status(Context context) {
        JSONObject result = new JSONObject();
        try {
            android.os.BatteryManager battery =
                    (android.os.BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            PowerManager power =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            AlarmManager alarm =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            result.put(
                    "battery_percent",
                    battery == null
                            ? -1
                            : battery.getIntProperty(
                                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY));
            result.put("screen_on", power != null && power.isInteractive());
            result.put("android_api", Build.VERSION.SDK_INT);
            result.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            result.put("accessibility_enabled", AutomationService.get() != null);
            result.put("automation_enabled", AppState.automationEnabled(context));

            if (Build.VERSION.SDK_INT >= 31 && alarm != null) {
                result.put("exact_alarm_allowed", alarm.canScheduleExactAlarms());
            } else {
                result.put("exact_alarm_allowed", true);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static boolean launch(Context context, String packageName) {
        try {
            if (packageName == null || packageName.trim().isEmpty()) return false;

            Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(packageName.trim());
            if (intent == null) return false;

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void openAccessibility(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    public static void openBattery(Context context) {
        try {
            context.startActivity(new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    public static void openExactAlarm(Context context) {
        if (Build.VERSION.SDK_INT < 31) return;

        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(android.net.Uri.parse(
                    "package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
            try {
                context.startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignoredAgain) {
            }
        }
    }
}
