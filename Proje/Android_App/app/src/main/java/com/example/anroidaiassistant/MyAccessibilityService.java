package com.example.anroidaiassistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static MyAccessibilityService instance;
    private static final int RESTART_DELAY_FAST_MS = 200;
    private static final int RESTART_DELAY_SLOW_MS = 800;
    private static final long DEFAULT_GESTURE_DURATION_MS = 350L;
    private static final long DEFAULT_SCROLL_DURATION_MS = 450L;
    private static final long PHOTO_CAPTURE_START_DELAY_MS = 1000L;
    private static final long PHOTO_CAPTURE_RETRY_DELAY_MS = 450L;
    private static final int MAX_PHOTO_CAPTURE_ATTEMPTS = 4;
    private static final int[] STREAMS_TO_MUTE = {
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_MUSIC
    };

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private boolean isSpellAppMode = false;
    private boolean isRecognitionSessionActive = false;
    private boolean areRecognizerSoundsMuted = false;
    private String selectedLanguage = "TR";
    
    private ApiService apiService;
    private CommandExecutor commandExecutor;
    private AudioManager audioManager;

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvOverlayText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable restartListeningRunnable = this::startListeningSession;

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        
        apiService = RetrofitClient.getClient().create(ApiService.class);
        commandExecutor = new CommandExecutor(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        setupSpeechRecognizer();
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show();
    }

    private void setupSpeechRecognizer() {
        destroySpeechRecognizer();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognizer mevcut değil.", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        updateLanguage(selectedLanguage);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                updateOverlayText("Listening...");
            }
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                isRecognitionSessionActive = false;
                if (!isListening) {
                    return;
                }

                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    case SpeechRecognizer.ERROR_CLIENT:
                        updateOverlayText("Listening...");
                        scheduleListeningRestart(RESTART_DELAY_FAST_MS);
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    case SpeechRecognizer.ERROR_SERVER:
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        setupSpeechRecognizer();
                        scheduleListeningRestart(RESTART_DELAY_SLOW_MS);
                        break;
                    default:
                        scheduleListeningRestart(RESTART_DELAY_SLOW_MS);
                        break;
                }
            }

            @Override
            public void onResults(Bundle results) {
                isRecognitionSessionActive = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    updateOverlayText("Input: " + spokenText);
                    if (consumeSpellAppMode()) {
                        if (commandExecutor != null) {
                            commandExecutor.handleSpelledAppCandidate(spokenText);
                        }
                    } else {
                        sendPredictionRequest(spokenText);
                    }
                }
                if (isListening) {
                    scheduleListeningRestart(RESTART_DELAY_FAST_MS);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    updateOverlayText("Hearing: " + matches.get(0));
                }
            }
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void destroySpeechRecognizer() {
        mainHandler.removeCallbacks(restartListeningRunnable);
        isRecognitionSessionActive = false;

        if (speechRecognizer == null) {
            return;
        }

        try {
            speechRecognizer.cancel();
        } catch (Exception ignored) {}

        speechRecognizer.destroy();
        speechRecognizer = null;
    }

    private void startListeningSession() {
        mainHandler.removeCallbacks(restartListeningRunnable);
        if (!isListening || speechRecognizer == null || isRecognitionSessionActive) {
            return;
        }

        try {
            isRecognitionSessionActive = true;
            speechRecognizer.startListening(speechRecognizerIntent);
        } catch (Exception ignored) {
            isRecognitionSessionActive = false;
            setupSpeechRecognizer();
            scheduleListeningRestart(RESTART_DELAY_SLOW_MS);
        }
    }

    private void scheduleListeningRestart(int delayMillis) {
        if (!isListening) {
            return;
        }

        mainHandler.removeCallbacks(restartListeningRunnable);
        mainHandler.postDelayed(restartListeningRunnable, delayMillis);
    }

    private void setRecognizerSoundsMuted(boolean muted) {
        if (audioManager == null || areRecognizerSoundsMuted == muted) {
            return;
        }

        for (int stream : STREAMS_TO_MUTE) {
            try {
                audioManager.adjustStreamVolume(
                        stream,
                        muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                        0
                );
            } catch (Exception ignored) {}
        }

        areRecognizerSoundsMuted = muted;
    }

    public void updateLanguage(String lang) {
        this.selectedLanguage = lang;
        if (speechRecognizerIntent == null) {
            return;
        }

        if (lang.equals("TR")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        } else if (lang.equals("EN")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        } else if (lang.equals("AR")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        }
    }

    public void startContinuousListening() {
        mainHandler.removeCallbacks(restartListeningRunnable);
        isListening = true;
        setRecognizerSoundsMuted(true);
        showOverlay();
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }
        startListeningSession();
    }

    public void stopContinuousListening() {
        isListening = false;
        isSpellAppMode = false;
        AssistantSession.endSession();
        mainHandler.removeCallbacks(restartListeningRunnable);
        setRecognizerSoundsMuted(false);
        isRecognitionSessionActive = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
        hideOverlay();
    }

    private void showOverlay() {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
            tvOverlayText = overlayView.findViewById(R.id.tv_overlay_text);
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 20;
            params.y = 100;
            
            windowManager.addView(overlayView, params);
        }
    }

    private void hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            tvOverlayText = null;
        }
    }

    private void updateOverlayText(String text) {
        if (tvOverlayText != null) {
            tvOverlayText.setText(text);
        }
    }

    public void showFeedback(String text) {
        updateOverlayText(text);
    }

    public void submitTextCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            showFeedback("Type a command first");
            return;
        }
        updateOverlayText("Input: " + text.trim());
        sendPredictionRequest(text.trim());
    }

    private void sendPredictionRequest(String text) {
        PredictRequest request = new PredictRequest(text, selectedLanguage, AssistantSession.getSessionId());
        apiService.predict(request).enqueue(new Callback<PredictResponse>() {
            @Override
            public void onResponse(Call<PredictResponse> call, Response<PredictResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PredictResponse body = response.body();
                    
                    // Update MainActivity UI if it's visible
                    MainActivity mainActivity = MainActivity.getInstance();
                    if (mainActivity != null) {
                        mainActivity.updateResultUI(body);
                    }
                    
                    commandExecutor.executeCommand(body);
                } else {
                    Log.e(TAG, "Prediction failed. httpCode=" + response.code());
                    showRequestError("No response from backend");
                }
            }
            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                Log.e(TAG, "Prediction request failed", t);
                showRequestError("Backend unavailable");
            }
        });
    }

    private void showRequestError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.showAssistantMessage(message);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
        setRecognizerSoundsMuted(false);
        destroySpeechRecognizer();
        instance = null;
    }

    public boolean isContinuousListeningActive() {
        return isListening;
    }

    public void enableSpellAppMode() {
        isSpellAppMode = true;
        updateOverlayText("Spell app name...");
    }

    private boolean consumeSpellAppMode() {
        boolean shouldConsume = isSpellAppMode;
        isSpellAppMode = false;
        return shouldConsume;
    }

    public void performHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void performBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void performRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public void performNotifications() { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }

    public void clickNodeByText(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
    }

    public boolean capturePhoto() {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            schedulePhotoCaptureAttempt(1, PHOTO_CAPTURE_START_DELAY_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void schedulePhotoCaptureAttempt(int attempt, long delayMillis) {
        mainHandler.postDelayed(() -> tryCapturePhoto(attempt), delayMillis);
    }

    private void tryCapturePhoto(int attempt) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null && clickCameraShutter(rootNode)) {
            return;
        }

        if (attempt < MAX_PHOTO_CAPTURE_ATTEMPTS) {
            schedulePhotoCaptureAttempt(attempt + 1, PHOTO_CAPTURE_RETRY_DELAY_MS);
            return;
        }

        tapByRatio(0.50f, 0.87f);
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

        String normalizedValue = value.toString().trim().toLowerCase(Locale.US);
        return normalizedValue.contains("shutter")
                || normalizedValue.contains("capture")
                || normalizedValue.contains("take picture")
                || normalizedValue.contains("take photo")
                || normalizedValue.contains("camera_button")
                || normalizedValue.contains("foto")
                || normalizedValue.contains("cek");
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

    private boolean scrollDown() {
        if (performScrollActionOnNodeTree(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true;
        }
        return scrollByRatio(0.50f, 0.78f, 0.50f, 0.24f);
    }

    private boolean scrollUp() {
        if (performScrollActionOnNodeTree(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            return true;
        }
        return scrollByRatio(0.50f, 0.24f, 0.50f, 0.78f);
    }

    private boolean performScrollActionOnNodeTree(int action) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
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

    private String normalizeDirection(String direction) {
        if (direction == null) {
            return "";
        }
        return direction.trim().toLowerCase(Locale.US);
    }

    private boolean swipeByRatio(
            float startXRatio,
            float startYRatio,
            float endXRatio,
            float endYRatio,
            long durationMillis
    ) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int startX = clamp(Math.round(width * startXRatio), 1, width - 1);
        int startY = clamp(Math.round(height * startYRatio), 1, height - 1);
        int endX = clamp(Math.round(width * endXRatio), 1, width - 1);
        int endY = clamp(Math.round(height * endYRatio), 1, height - 1);

        return swipe(startX, startY, endX, endY, durationMillis);
    }

    private boolean tapByRatio(float xRatio, float yRatio) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        if (width <= 0 || height <= 0) {
            return false;
        }

        int x = clamp(Math.round(width * xRatio), 1, width - 1);
        int y = clamp(Math.round(height * yRatio), 1, height - 1);
        return tap(x, y);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean tap(int x, int y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 60L));
        return dispatchGesture(gestureBuilder.build(), null, null);
    }

    private boolean swipe(int startX, int startY, int endX, int endY, long durationMillis) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMillis));
        boolean dispatched = dispatchGesture(gestureBuilder.build(), null, null);
        return dispatched;
    }
}
