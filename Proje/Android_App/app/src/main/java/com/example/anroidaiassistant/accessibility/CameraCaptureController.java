package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public final class CameraCaptureController {
    private static final long PHOTO_CAPTURE_ACTIVE_CAMERA_DELAY_MS = 150L;
    private static final long PHOTO_CAPTURE_START_DELAY_MS = 1800L;
    private static final long PHOTO_CAPTURE_RETRY_DELAY_MS = 700L;
    private static final int MAX_PHOTO_CAPTURE_ATTEMPTS = 8;

    private final AccessibilityService service;
    private final Handler mainHandler;
    private final GestureController gestureController;

    public CameraCaptureController(
            AccessibilityService service,
            Handler mainHandler,
            GestureController gestureController
    ) {
        this.service = service;
        this.mainHandler = mainHandler;
        this.gestureController = gestureController;
    }

    public boolean capturePhoto() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (isCameraWindow(rootNode)) {
            if (clickCameraShutter(rootNode)) {
                return true;
            }
            schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_ACTIVE_CAMERA_DELAY_MS);
            return true;
        }

        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(intent);
            schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_START_DELAY_MS);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isCameraWindow(AccessibilityNodeInfo rootNode) {
        return rootNode != null
                && (isCameraPackage(rootNode.getPackageName()) || hasLikelyCameraShutter(rootNode));
    }

    private boolean isCameraPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }

        String normalizedPackageName = normalizeKeywordText(packageName.toString());
        return normalizedPackageName.contains("camera")
                || normalizedPackageName.contains("kamera")
                || normalizedPackageName.contains("googlecamera")
                || normalizedPackageName.contains("snapcam");
    }

    private boolean hasLikelyCameraShutter(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        if (containsLikelyShutterKeyword(node.getViewIdResourceName())
                || containsLikelyShutterKeyword(node.getContentDescription())) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasLikelyCameraShutter(node.getChild(i))) {
                return true;
            }
        }

        return false;
    }

    private void schedulePhotoCaptureAttempt(int attempt, long delayMillis) {
        mainHandler.postDelayed(() -> tryCapturePhoto(attempt), delayMillis);
    }

    private void tryCapturePhoto(int attempt) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode != null && clickCameraShutter(rootNode)) {
            return;
        }

        if (attempt < MAX_PHOTO_CAPTURE_ATTEMPTS) {
            schedulePhotoCaptureAttempt(attempt + 1, PHOTO_CAPTURE_RETRY_DELAY_MS);
            return;
        }

        gestureController.tapByRatio(0.50f, 0.90f);
    }

    private boolean clickCameraShutter(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        if (isCameraShutterNode(node)) {
            AccessibilityNodeInfo clickableNode = findClickableNode(node);
            if (clickableNode != null && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }

            if (gestureController.tapNodeCenter(node)) {
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && clickCameraShutter(child)) {
                return true;
            }
        }

        return false;
    }

    private boolean isCameraShutterNode(AccessibilityNodeInfo node) {
        return containsCameraKeyword(node.getViewIdResourceName())
                || containsCameraKeyword(node.getText())
                || containsCameraKeyword(node.getContentDescription());
    }

    private boolean containsCameraKeyword(CharSequence value) {
        if (value == null) {
            return false;
        }

        String normalizedValue = normalizeKeywordText(value.toString());
        return normalizedValue.contains("shutter")
                || normalizedValue.contains("capture")
                || normalizedValue.contains("snap")
                || normalizedValue.contains("take picture")
                || normalizedValue.contains("take photo")
                || normalizedValue.contains("camera_button")
                || normalizedValue.contains("shutter_button")
                || normalizedValue.contains("capture_button")
                || normalizedValue.contains("button_capture")
                || normalizedValue.contains("normal_center_button")
                || normalizedValue.contains("deklansor")
                || normalizedValue.contains("foto")
                || normalizedValue.contains("cek");
    }

    private boolean containsLikelyShutterKeyword(CharSequence value) {
        if (value == null) {
            return false;
        }

        String normalizedValue = normalizeKeywordText(value.toString());
        return normalizedValue.contains("shutter")
                || normalizedValue.contains("capture")
                || normalizedValue.contains("camera_button")
                || normalizedValue.contains("shutter_button")
                || normalizedValue.contains("capture_button")
                || normalizedValue.contains("button_capture")
                || normalizedValue.contains("normal_center_button")
                || normalizedValue.contains("deklansor");
    }

    private String normalizeKeywordText(String value) {
        return value.trim()
                .toLowerCase(Locale.US)
                .replace('\u00e7', 'c')
                .replace('\u011f', 'g')
                .replace('\u0131', 'i')
                .replace('\u00f6', 'o')
                .replace('\u015f', 's')
                .replace('\u00fc', 'u');
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
