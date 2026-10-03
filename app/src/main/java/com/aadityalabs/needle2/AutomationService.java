package com.aadityalabs.needle2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutomationService extends AccessibilityService {
    private static volatile AutomationService instance;

    public static AutomationService get() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private AccessibilityNodeInfo root() {
        try {
            return getRootInActiveWindow();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public synchronized String dumpScreen() {
        AccessibilityNodeInfo root = root();
        if (root == null) {
            return "{\"ok\":false,\"error\":\"accessibility root unavailable\"}";
        }

        ArrayList<String> out = new ArrayList<>();
        collect(root, out, 0);

        StringBuilder builder = new StringBuilder("{\"ok\":true,\"package\":\"")
                .append(esc(String.valueOf(root.getPackageName())))
                .append("\",\"nodes\":[");

        for (int i = 0; i < out.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(out.get(i));
        }

        return builder.append("]}").toString();
    }

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || out.size() >= 50 || depth > 12) {
            return;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? ""
                : node.getContentDescription().toString();

        if (text.length() > 80) {
            text = text.substring(0, 80);
        }
        if (description.length() > 80) {
            description = description.substring(0, 80);
        }

        if (!text.isEmpty()
                || !description.isEmpty()
                || node.isClickable()
                || node.isEditable()
                || node.isScrollable()) {
            out.add("{\"text\":\"" + esc(text)
                    + "\",\"desc\":\"" + esc(description)
                    + "\",\"clickable\":" + node.isClickable()
                    + ",\"editable\":" + node.isEditable()
                    + ",\"scrollable\":" + node.isScrollable()
                    + ",\"bounds\":\"" + bounds.left + "," + bounds.top + ","
                    + bounds.right + "," + bounds.bottom + "\"}");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), out, depth + 1);
        }
    }

    private AccessibilityNodeInfo find(String query) {
        AccessibilityNodeInfo root = root();
        if (root == null) {
            return null;
        }

        List<AccessibilityNodeInfo> exact = root.findAccessibilityNodeInfosByText(query);
        if (exact != null && !exact.isEmpty()) {
            return exact.get(0);
        }

        return findRecursive(root, query.toLowerCase(Locale.US));
    }

    private AccessibilityNodeInfo findRecursive(AccessibilityNodeInfo node, String query) {
        if (node == null) {
            return null;
        }

        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();

        if ((text != null && text.toString().toLowerCase(Locale.US).contains(query))
                || (description != null
                && description.toString().toLowerCase(Locale.US).contains(query))) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findRecursive(node.getChild(i), query);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return node;
    }

    public boolean clickText(String query) {
        AccessibilityNodeInfo node = find(query);
        if (node == null) {
            return false;
        }

        AccessibilityNodeInfo target = clickable(node);
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }

        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        return tap((bounds.left + bounds.right) / 2f,
                (bounds.top + bounds.bottom) / 2f, 120);
    }

    public boolean longClick(String query, long duration) {
        AccessibilityNodeInfo node = find(query);
        if (node == null) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= 21
                && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, new Bundle())) {
            return true;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return tap((bounds.left + bounds.right) / 2f,
                (bounds.top + bounds.bottom) / 2f, duration);
    }

    public boolean typeText(String target, String value) {
        AccessibilityNodeInfo node = (target == null || target.isEmpty())
                ? firstEditable(root())
                : find(target);

        if (node == null) {
            return false;
        }

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
        );
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isEditable()) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = firstEditable(node.getChild(i));
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public boolean scroll(String direction) {
        AccessibilityNodeInfo node = scrollable(root());
        if (node == null) {
            return false;
        }

        return "up".equalsIgnoreCase(direction)
                ? node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                : node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = scrollable(node.getChild(i));
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public boolean tap(float x, float y, long duration) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, Math.max(80, duration)))
                .build();

        return dispatchGesture(gesture, null, null);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long duration) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, Math.max(100, duration)))
                .build();

        return dispatchGesture(gesture, null, null);
    }

    public boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private static String esc(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
