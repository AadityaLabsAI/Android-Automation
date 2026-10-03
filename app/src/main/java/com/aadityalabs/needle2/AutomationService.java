package com.aadityalabs.needle2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        ArrayList<String> nodes = new ArrayList<>();
        collect(root, nodes, 0);

        StringBuilder json = new StringBuilder("{\"ok\":true,\"package\":\"")
                .append(esc(String.valueOf(root.getPackageName())))
                .append("\",\"nodes\":[");

        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) json.append(',');
            json.append(nodes.get(i));
        }

        return json.append("]}").toString();
    }

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || out.size() >= 50 || depth > 12) return;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        boolean password = Build.VERSION.SDK_INT >= 18 && node.isPassword();
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? ""
                : node.getContentDescription().toString();

        if (password) {
            text = "[redacted]";
        }
        if (text.length() > 80) text = text.substring(0, 80);
        if (description.length() > 80) description = description.substring(0, 80);

        String className = node.getClassName() == null ? "" : node.getClassName().toString();
        String viewId = "";
        if (Build.VERSION.SDK_INT >= 18) {
            try {
                viewId = node.getViewIdResourceName();
            } catch (RuntimeException ignored) {
            }
        }

        if (!text.isEmpty()
                || !description.isEmpty()
                || !className.isEmpty()
                || !viewId.isEmpty()
                || node.isClickable()
                || node.isEditable()
                || node.isScrollable()) {
            out.add("{\"text\":\"" + esc(text)
                    + "\",\"desc\":\"" + esc(description)
                    + "\",\"class\":\"" + esc(className)
                    + "\",\"view_id\":\"" + esc(viewId)
                    + "\",\"clickable\":" + node.isClickable()
                    + ",\"editable\":" + node.isEditable()
                    + ",\"focused\":" + node.isFocused()
                    + ",\"enabled\":" + node.isEnabled()
                    + ",\"scrollable\":" + node.isScrollable()
                    + ",\"password\":" + password
                    + ",\"bounds\":\"" + bounds.left + "," + bounds.top + ","
                    + bounds.right + "," + bounds.bottom + "\"}");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), out, depth + 1);
        }
    }

    private AccessibilityNodeInfo find(String query) {
        if (query == null || query.trim().isEmpty()) return null;

        AccessibilityNodeInfo root = root();
        if (root == null) return null;

        String normalized = query.trim();
        List<AccessibilityNodeInfo> exact = root.findAccessibilityNodeInfosByText(normalized);
        if (exact != null) {
            for (AccessibilityNodeInfo node : exact) {
                if (node != null && !isPasswordNode(node)) return node;
            }
        }

        return findRecursive(root, normalized.toLowerCase(Locale.US));
    }

    private AccessibilityNodeInfo findRecursive(AccessibilityNodeInfo node, String query) {
        if (node == null) return null;

        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        boolean password = isPasswordNode(node);

        if (!password && ((text != null
                && text.toString().toLowerCase(Locale.US).contains(query))
                || (description != null
                && description.toString().toLowerCase(Locale.US).contains(query)))) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findRecursive(node.getChild(i), query);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isPasswordNode(AccessibilityNodeInfo node) {
        return Build.VERSION.SDK_INT >= 18 && node.isPassword();
    }

    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current.isClickable() && current.isEnabled()) return current;
            current = current.getParent();
        }
        return node != null && node.isEnabled() ? node : null;
    }

    public boolean clickText(String query) {
        AccessibilityNodeInfo node = find(query);
        AccessibilityNodeInfo target = clickable(node);
        if (target == null) return false;

        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;

        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        return tap((bounds.left + bounds.right) / 2f,
                (bounds.top + bounds.bottom) / 2f, 120);
    }

    public boolean longClick(String query, long duration) {
        AccessibilityNodeInfo node = find(query);
        if (node == null) return false;

        if (Build.VERSION.SDK_INT >= 21
                && node.isEnabled()
                && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, new Bundle())) {
            return true;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return tap((bounds.left + bounds.right) / 2f,
                (bounds.top + bounds.bottom) / 2f,
                Math.max(300, Math.min(3000, duration)));
    }

    public boolean typeText(String target, String value) {
        if (value == null) return false;

        AccessibilityNodeInfo node = (target == null || target.trim().isEmpty())
                ? firstEditable(root())
                : editableTarget(find(target));

        if (node == null || !node.isEnabled()) return false;

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo editableTarget(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = editableTarget(node.getChild(i));
            if (found != null) return found;
        }

        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isEditable() && parent.isEnabled()) return parent;
        return null;
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = firstEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean scroll(String direction) {
        AccessibilityNodeInfo node = scrollable(root());
        if (node == null) return false;

        String normalized = direction == null ? "down" : direction.trim();
        if (!"up".equalsIgnoreCase(normalized) && !"down".equalsIgnoreCase(normalized)) {
            return false;
        }

        return "up".equalsIgnoreCase(normalized)
                ? node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                : node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable() && node.isEnabled()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = scrollable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean tap(float x, float y, long duration) {
        if (!validPoint(x, y)) return false;

        Path path = new Path();
        path.moveTo(x, y);

        long bounded = Math.max(80L, Math.min(5000L, duration));
        return dispatchAndWait(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, bounded))
                .build(), 6000L);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long duration) {
        if (!validPoint(x1, y1) || !validPoint(x2, y2)) return false;

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        long bounded = Math.max(100L, Math.min(5000L, duration));
        return dispatchAndWait(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, bounded))
                .build(), 6000L);
    }

    private boolean dispatchAndWait(GestureDescription gesture, long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] completed = {false};

        try {
            boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription description) {
                    completed[0] = true;
                    latch.countDown();
                }

                @Override
                public void onCancelled(GestureDescription description) {
                    completed[0] = false;
                    latch.countDown();
                }
            }, null);

            if (!dispatched) return false;
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return completed[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean validPoint(float x, float y) {
        if (x < 0 || y < 0) return false;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return x < metrics.widthPixels && y < metrics.heightPixels;
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
