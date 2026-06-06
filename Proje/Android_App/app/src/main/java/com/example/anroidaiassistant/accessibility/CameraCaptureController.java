package com.example.anroidaiassistant.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public final class CameraCaptureController {
    private static final long PHOTO_CAPTURE_ACTIVE_CAMERA_DELAY_MS = 150L;
    private static final long PHOTO_CAPTURE_START_DELAY_MS = 1800L;
    private static final long PHOTO_CAPTURE_RETRY_DELAY_MS = 700L;
    private static final long CAMERA_SWITCH_SETTLE_DELAY_MS = 1800L;
    private static final int MAX_PHOTO_CAPTURE_ATTEMPTS = 8;
    private static final int MAX_CAMERA_SWITCH_DISCOVERY_ATTEMPTS = 3;
    private static final int MAX_SWITCHED_CAMERA_SHUTTER_ATTEMPTS = 3;

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
        return capturePhoto(null);
    }

    public boolean capturePhoto(String camera) {
        String normalizedCamera = normalizeCamera(camera);
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (isCameraWindow(rootNode)) {
            if (!normalizedCamera.isEmpty()) {
                schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_ACTIVE_CAMERA_DELAY_MS, normalizedCamera);
                return true;
            }

            if (clickCameraShutter(rootNode)) {
                return true;
            }
            schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_ACTIVE_CAMERA_DELAY_MS);
            return true;
        }

        return openCameraAndScheduleCapture(normalizedCamera);
    }

    private boolean openCameraAndScheduleCapture(String camera) {
        try {
            Intent intent = buildCameraIntent(camera);
            service.startActivity(intent);
            schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_START_DELAY_MS, camera);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private Intent buildCameraIntent(String camera) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
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
        schedulePhotoCaptureAttempt(attempt, delayMillis, "");
    }

    private void schedulePhotoCaptureAttempt(int attempt, long delayMillis, String camera) {
        schedulePhotoCaptureAttempt(attempt, delayMillis, camera, false);
    }

    private void schedulePhotoCaptureAttempt(
            int attempt,
            long delayMillis,
            String camera,
            boolean cameraSwitchAttempted
    ) {
        String targetCamera = normalizeCamera(camera);
        mainHandler.postDelayed(
                () -> tryCapturePhoto(attempt, targetCamera, cameraSwitchAttempted),
                delayMillis
        );
    }

    private void tryCapturePhoto(int attempt, String targetCamera, boolean cameraSwitchAttempted) {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (cameraSwitchAttempted) {
            captureAfterCameraSwitch(rootNode, attempt, targetCamera);
            return;
        }

        if (rootNode != null && !targetCamera.isEmpty() && !cameraSwitchAttempted) {
            CameraFacingResult facingResult = ensureCameraFacing(rootNode, targetCamera, attempt);
            if (facingResult == CameraFacingResult.SWITCHED) {
                schedulePhotoCaptureAttempt(
                        attempt + 1,
                        CAMERA_SWITCH_SETTLE_DELAY_MS,
                        targetCamera,
                        true
                );
                return;
            }
            if (facingResult == CameraFacingResult.UNKNOWN && !cameraSwitchAttempted) {
                if (attempt < MAX_CAMERA_SWITCH_DISCOVERY_ATTEMPTS) {
                    schedulePhotoCaptureAttempt(attempt + 1, PHOTO_CAPTURE_RETRY_DELAY_MS, targetCamera);
                    return;
                }
                if (attempt == MAX_CAMERA_SWITCH_DISCOVERY_ATTEMPTS && clickLikelyCameraSwitch(rootNode)) {
                    schedulePhotoCaptureAttempt(
                            attempt + 1,
                            CAMERA_SWITCH_SETTLE_DELAY_MS,
                            targetCamera,
                            true
                    );
                    return;
                }
            }
        }

        if (rootNode != null && clickCameraShutter(rootNode)) {
            return;
        }

        if (attempt < MAX_SWITCHED_CAMERA_SHUTTER_ATTEMPTS) {
            schedulePhotoCaptureAttempt(
                    attempt + 1,
                    PHOTO_CAPTURE_RETRY_DELAY_MS,
                    targetCamera,
                    cameraSwitchAttempted
            );
            return;
        }

        gestureController.tapByRatio(0.50f, 0.90f);
    }

    private void captureAfterCameraSwitch(
            AccessibilityNodeInfo rootNode,
            int attempt,
            String targetCamera
    ) {
        if (rootNode != null && clickLikelyCameraShutterByLayout(rootNode)) {
            return;
        }

        if (tapLikelyCameraShutter()) {
            return;
        }

        if (attempt < MAX_SWITCHED_CAMERA_SHUTTER_ATTEMPTS) {
            schedulePhotoCaptureAttempt(
                    attempt + 1,
                    PHOTO_CAPTURE_RETRY_DELAY_MS,
                    targetCamera,
                    true
            );
            return;
        }

        tapLikelyCameraShutter();
    }

    private CameraFacingResult ensureCameraFacing(
            AccessibilityNodeInfo rootNode,
            String targetCamera,
            int attempt
    ) {
        AccessibilityNodeInfo switchNode = findCameraSwitchNode(rootNode);
        if (switchNode == null) {
            return CameraFacingResult.UNKNOWN;
        }

        String switchText = normalizeKeywordText(joinNodeText(switchNode));
        boolean switchGoesToFront = containsFrontCameraKeyword(switchText);
        boolean switchGoesToBack = containsBackCameraKeyword(switchText);

        if ("front".equals(targetCamera)) {
            if (switchGoesToBack) {
                return CameraFacingResult.READY;
            }
            if (switchGoesToFront || (!switchGoesToBack && attempt == 1)) {
                return clickCameraSwitch(switchNode)
                        ? CameraFacingResult.SWITCHED
                        : CameraFacingResult.UNKNOWN;
            }
        }

        if ("back".equals(targetCamera)) {
            if (switchGoesToFront) {
                return CameraFacingResult.READY;
            }
            if (switchGoesToBack || (!switchGoesToFront && attempt == 1)) {
                return clickCameraSwitch(switchNode)
                        ? CameraFacingResult.SWITCHED
                        : CameraFacingResult.UNKNOWN;
            }
        }

        return CameraFacingResult.UNKNOWN;
    }

    private AccessibilityNodeInfo findCameraSwitchNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        if (isCameraSwitchNode(node)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findCameraSwitchNode(child);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean isCameraSwitchNode(AccessibilityNodeInfo node) {
        String normalizedText = normalizeKeywordText(joinNodeText(node));
        return normalizedText.contains("switch camera")
                || normalizedText.contains("flip camera")
                || normalizedText.contains("swap camera")
                || normalizedText.contains("change camera")
                || normalizedText.contains("reverse camera")
                || normalizedText.contains("switch to front")
                || normalizedText.contains("switch to back")
                || normalizedText.contains("switch to rear")
                || normalizedText.contains("front camera")
                || normalizedText.contains("back camera")
                || normalizedText.contains("rear camera")
                || normalizedText.contains("camera switch")
                || normalizedText.contains("camera_switch")
                || normalizedText.contains("switch_camera")
                || normalizedText.contains("flip_camera")
                || normalizedText.contains("camera_flip")
                || normalizedText.contains("camera_picker")
                || normalizedText.contains("camera_facing")
                || normalizedText.contains("toggle_camera")
                || normalizedText.contains("lens_toggle")
                || normalizedText.contains("camera_toggle")
                || normalizedText.contains("kamera cevir")
                || normalizedText.contains("kamera degistir")
                || normalizedText.contains("kameraya gec")
                || normalizedText.contains("cevir")
                || normalizedText.contains("degistir")
                || normalizedText.contains("kamerayi cevir")
                || normalizedText.contains("kamerayi degistir")
                || normalizedText.contains("\u0628\u062f\u0644 \u0627\u0644\u0643\u0627\u0645\u064a\u0631\u0627")
                || normalizedText.contains("\u062a\u0628\u062f\u064a\u0644 \u0627\u0644\u0643\u0627\u0645\u064a\u0631\u0627")
                || normalizedText.contains("\u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0643\u0627\u0645\u064a\u0631\u0627")
                || normalizedText.contains("\u0642\u0644\u0628 \u0627\u0644\u0643\u0627\u0645\u064a\u0631\u0627");
    }

    private boolean clickCameraSwitch(AccessibilityNodeInfo switchNode) {
        AccessibilityNodeInfo clickableNode = findClickableNode(switchNode);
        if (clickableNode != null && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return gestureController.tapNodeCenter(switchNode);
    }

    private boolean clickLikelyCameraSwitch(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo switchNode = findLikelyCameraSwitchNodeByLayout(rootNode);
        if (switchNode != null && clickCameraSwitch(switchNode)) {
            return true;
        }
        return gestureController.tapByRatio(0.82f, 0.82f);
    }

    private boolean clickLikelyCameraShutterByLayout(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo shutterNode = findLikelyCameraShutterNodeByLayout(rootNode);
        if (shutterNode == null) {
            return false;
        }

        AccessibilityNodeInfo clickableNode = findClickableNode(shutterNode);
        if (clickableNode != null && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return gestureController.tapNodeCenter(shutterNode);
    }

    private boolean tapLikelyCameraShutter() {
        return gestureController.tapByRatio(0.50f, 0.88f)
                || gestureController.tapByRatio(0.50f, 0.82f)
                || gestureController.tapByRatio(0.50f, 0.90f);
    }

    private AccessibilityNodeInfo findLikelyCameraShutterNodeByLayout(AccessibilityNodeInfo rootNode) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        CameraSwitchCandidate candidate = findLikelyCameraShutterNodeByLayout(
                rootNode,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                null
        );
        return candidate == null ? null : candidate.node;
    }

    private CameraSwitchCandidate findLikelyCameraShutterNodeByLayout(
            AccessibilityNodeInfo node,
            int screenWidth,
            int screenHeight,
            CameraSwitchCandidate bestCandidate
    ) {
        if (node == null || screenWidth <= 0 || screenHeight <= 0) {
            return bestCandidate;
        }

        int score = scoreLikelyCameraShutterNode(node, screenWidth, screenHeight);
        if (score > 0 && (bestCandidate == null || score > bestCandidate.score)) {
            bestCandidate = new CameraSwitchCandidate(node, score);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            bestCandidate = findLikelyCameraShutterNodeByLayout(
                    node.getChild(i),
                    screenWidth,
                    screenHeight,
                    bestCandidate
            );
        }
        return bestCandidate;
    }

    private int scoreLikelyCameraShutterNode(
            AccessibilityNodeInfo node,
            int screenWidth,
            int screenHeight
    ) {
        if (isCameraSwitchNode(node) || findClickableNode(node) == null) {
            return 0;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return 0;
        }

        float centerXRatio = bounds.centerX() / (float) screenWidth;
        float centerYRatio = bounds.centerY() / (float) screenHeight;
        float widthRatio = bounds.width() / (float) screenWidth;
        float heightRatio = bounds.height() / (float) screenHeight;

        if (centerXRatio < 0.35f
                || centerXRatio > 0.65f
                || centerYRatio < 0.65f
                || centerYRatio > 0.95f
                || widthRatio > 0.35f
                || heightRatio > 0.25f) {
            return 0;
        }

        int score = 8;
        if (isCameraShutterNode(node)) {
            score += 8;
        }
        if (centerYRatio > 0.75f) {
            score += 2;
        }
        return score;
    }

    private AccessibilityNodeInfo findLikelyCameraSwitchNodeByLayout(AccessibilityNodeInfo rootNode) {
        DisplayMetrics displayMetrics = service.getResources().getDisplayMetrics();
        CameraSwitchCandidate candidate = findLikelyCameraSwitchNodeByLayout(
                rootNode,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                null
        );
        return candidate == null ? null : candidate.node;
    }

    private CameraSwitchCandidate findLikelyCameraSwitchNodeByLayout(
            AccessibilityNodeInfo node,
            int screenWidth,
            int screenHeight,
            CameraSwitchCandidate bestCandidate
    ) {
        if (node == null || screenWidth <= 0 || screenHeight <= 0) {
            return bestCandidate;
        }

        int score = scoreLikelyCameraSwitchNode(node, screenWidth, screenHeight);
        if (score > 0 && (bestCandidate == null || score > bestCandidate.score)) {
            bestCandidate = new CameraSwitchCandidate(node, score);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            bestCandidate = findLikelyCameraSwitchNodeByLayout(
                    node.getChild(i),
                    screenWidth,
                    screenHeight,
                    bestCandidate
            );
        }
        return bestCandidate;
    }

    private int scoreLikelyCameraSwitchNode(
            AccessibilityNodeInfo node,
            int screenWidth,
            int screenHeight
    ) {
        if (isCameraShutterNode(node) || findClickableNode(node) == null) {
            return 0;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return 0;
        }

        float centerXRatio = bounds.centerX() / (float) screenWidth;
        float centerYRatio = bounds.centerY() / (float) screenHeight;
        float widthRatio = bounds.width() / (float) screenWidth;
        float heightRatio = bounds.height() / (float) screenHeight;

        if (centerYRatio < 0.55f
                || centerYRatio > 0.95f
                || widthRatio > 0.35f
                || heightRatio > 0.25f
                || (centerXRatio > 0.35f && centerXRatio < 0.65f)) {
            return 0;
        }

        int score = centerXRatio > 0.65f ? 6 : 2;
        if (centerYRatio > 0.65f) {
            score += 2;
        }
        if (normalizeKeywordText(joinNodeText(node)).contains("camera")) {
            score += 3;
        }
        return score;
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

    private boolean containsFrontCameraKeyword(String normalizedText) {
        return normalizedText.contains("front")
                || normalizedText.contains("selfie")
                || normalizedText.contains("front facing")
                || normalizedText.contains("front-facing")
                || normalizedText.contains("selfie camera")
                || normalizedText.contains("on kamera")
                || normalizedText.contains("kamera on")
                || normalizedText.contains("\u0627\u0645\u0627\u0645\u064a")
                || normalizedText.contains("\u0627\u0644\u0627\u0645\u0627\u0645\u064a\u0629");
    }

    private boolean containsBackCameraKeyword(String normalizedText) {
        return normalizedText.contains("back")
                || normalizedText.contains("rear")
                || normalizedText.contains("back camera")
                || normalizedText.contains("rear camera")
                || normalizedText.contains("back facing")
                || normalizedText.contains("back-facing")
                || normalizedText.contains("arka")
                || normalizedText.contains("\u062e\u0644\u0641\u064a")
                || normalizedText.contains("\u0627\u0644\u062e\u0644\u0641\u064a\u0629");
    }

    private String joinNodeText(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        append(builder, node.getViewIdResourceName());
        return builder.toString();
    }

    private void append(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String normalizeCamera(String camera) {
        if (camera == null) {
            return "";
        }

        String normalizedCamera = normalizeKeywordText(camera);
        if (normalizedCamera.contains("front")
                || normalizedCamera.contains("selfie")
                || "on".equals(normalizedCamera)) {
            return "front";
        }
        if (normalizedCamera.contains("back")
                || normalizedCamera.contains("rear")
                || normalizedCamera.contains("arka")) {
            return "back";
        }
        return "";
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

    private enum CameraFacingResult {
        READY,
        SWITCHED,
        UNKNOWN
    }

    private static final class CameraSwitchCandidate {
        final AccessibilityNodeInfo node;
        final int score;

        CameraSwitchCandidate(AccessibilityNodeInfo node, int score) {
            this.node = node;
            this.score = score;
        }
    }
}
