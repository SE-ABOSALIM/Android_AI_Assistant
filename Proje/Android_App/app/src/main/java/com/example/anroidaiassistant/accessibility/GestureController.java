package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public final class GestureController {
    private static final long DEFAULT_GESTURE_DURATION_MS = 350L;
    private static final long DEFAULT_SCROLL_DURATION_MS = 650L;
    private static final long TAP_DURATION_MS = 60L;
    private static final long DOUBLE_TAP_GAP_MS = 120L;
    private static final long LONG_PRESS_DURATION_MS = 700L;

    private final AccessibilityService service;

    public GestureController(AccessibilityService service) {
        this.service = service;
    }

    public boolean scroll(String direction) {
        String normalizedDirection = normalizeDirection(direction);
        if ("down".equals(normalizedDirection)) {
            return scrollDown();
        }
        if ("up".equals(normalizedDirection)) {
            return scrollUp();
        }
        return false;
    }

    public boolean swipe(String direction) {
        switch (normalizeDirection(direction)) {
            case "left":
                return swipeByRatio(0.85f, 0.5f, 0.15f, 0.5f, DEFAULT_GESTURE_DURATION_MS);
            case "right":
                return swipeByRatio(0.15f, 0.5f, 0.85f, 0.5f, DEFAULT_GESTURE_DURATION_MS);
            default:
                return false;
        }
    }

    public boolean tapByRatio(float xRatio, float yRatio) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int x = clamp(Math.round(width * xRatio), 1, width - 1);
        int y = clamp(Math.round(height * yRatio), 1, height - 1);
        return tap(x, y);
    }

    public boolean doubleTapCenter() {
        return doubleTapByRatio(0.5f, 0.5f);
    }

    public boolean longPressCenter() {
        return longPressByRatio(0.5f, 0.5f);
    }

    public boolean tapNodeCenter(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }

        return tap(bounds.centerX(), bounds.centerY());
    }

    public boolean tapBoundsCenter(Rect bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }

        return tap(bounds.centerX(), bounds.centerY());
    }

    private boolean scrollDown() {
        if (scrollByRatio(0.52f, 0.82f, 0.52f, 0.16f)) {
            return true;
        }
        return performScrollActionOnNodeTree(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private boolean scrollUp() {
        if (isActivePackage("com.whatsapp")
                && scrollByRatio(0.52f, 0.42f, 0.52f, 0.88f)) {
            return true;
        }
        if (scrollByRatio(0.52f, 0.16f, 0.52f, 0.82f)) {
            return true;
        }
        return performScrollActionOnNodeTree(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    private boolean isActivePackage(String packagePrefix) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null || rootNode.getPackageName() == null) {
            return false;
        }
        return rootNode.getPackageName().toString().startsWith(packagePrefix);
    }

    private boolean performScrollActionOnNodeTree(int action) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        return performScrollAction(rootNode, action);
    }

    private boolean performScrollAction(AccessibilityNodeInfo node, int action) {
        if (node == null) {
            return false;
        }

        if ((node.isScrollable() || supportsAction(node, action)) && node.performAction(action)) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (performScrollAction(child, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean supportsAction(AccessibilityNodeInfo node, int actionId) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions == null) {
            return false;
        }

        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action != null && action.getId() == actionId) {
                return true;
            }
        }
        return false;
    }

    private boolean scrollByRatio(
            float startXRatio,
            float startYRatio,
            float endXRatio,
            float endYRatio
    ) {
        return swipeByRatio(
                startXRatio,
                startYRatio,
                endXRatio,
                endYRatio,
                DEFAULT_SCROLL_DURATION_MS
        );
    }

    private boolean swipeByRatio(
            float startXRatio,
            float startYRatio,
            float endXRatio,
            float endYRatio,
            long durationMillis
    ) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int startX = clamp(Math.round(width * startXRatio), 1, width - 1);
        int startY = clamp(Math.round(height * startYRatio), 1, height - 1);
        int endX = clamp(Math.round(width * endXRatio), 1, width - 1);
        int endY = clamp(Math.round(height * endYRatio), 1, height - 1);

        return performSwipe(startX, startY, endX, endY, durationMillis);
    }

    private boolean tap(int x, int y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, TAP_DURATION_MS));
        return service.dispatchGesture(gestureBuilder.build(), null, null);
    }

    private boolean doubleTapByRatio(float xRatio, float yRatio) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int x = clamp(Math.round(width * xRatio), 1, width - 1);
        int y = clamp(Math.round(height * yRatio), 1, height - 1);

        Path firstTap = new Path();
        firstTap.moveTo(x, y);
        Path secondTap = new Path();
        secondTap.moveTo(x, y);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(firstTap, 0, TAP_DURATION_MS));
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(
                secondTap,
                TAP_DURATION_MS + DOUBLE_TAP_GAP_MS,
                TAP_DURATION_MS
        ));
        return service.dispatchGesture(gestureBuilder.build(), null, null);
    }

    private boolean longPressByRatio(float xRatio, float yRatio) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int x = clamp(Math.round(width * xRatio), 1, width - 1);
        int y = clamp(Math.round(height * yRatio), 1, height - 1);

        Path pressPath = new Path();
        pressPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(pressPath, 0, LONG_PRESS_DURATION_MS));
        return service.dispatchGesture(gestureBuilder.build(), null, null);
    }

    private boolean performSwipe(int startX, int startY, int endX, int endY, long durationMillis) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMillis));
        return service.dispatchGesture(gestureBuilder.build(), null, null);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }
}
