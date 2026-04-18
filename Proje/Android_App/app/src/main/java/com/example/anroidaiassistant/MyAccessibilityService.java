package com.example.anroidaiassistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyAccessibilityService extends AccessibilityService {

    private static MyAccessibilityService instance;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private String selectedLanguage = "TR";
    
    private ApiService apiService;
    private CommandExecutor commandExecutor;

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvOverlayText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        
        setupSpeechRecognizer();
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show();
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
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
                if (isListening) {
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        restartListening(500);
                    } else {
                        restartListening(1000);
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    updateOverlayText("Input: " + spokenText);
                    sendPredictionRequest(spokenText);
                }
                if (isListening) {
                    restartListening(500);
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

    private void restartListening(int delayMillis) {
        mainHandler.postDelayed(() -> {
            if (isListening && speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.startListening(speechRecognizerIntent);
            }
        }, delayMillis);
    }

    public void updateLanguage(String lang) {
        this.selectedLanguage = lang;
        if (lang.equals("TR")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        } else if (lang.equals("EN")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        } else if (lang.equals("AR")) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        }
    }

    public void startContinuousListening() {
        isListening = true;
        showOverlay();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechRecognizerIntent);
        }
    }

    public void stopContinuousListening() {
        isListening = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
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

    private void sendPredictionRequest(String text) {
        PredictRequest request = new PredictRequest(text, selectedLanguage);
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
                }
            }
            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {}
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        instance = null;
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

    public void scroll(boolean forward) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        findAndScroll(rootNode, forward);
    }

    private boolean findAndScroll(AccessibilityNodeInfo node, boolean forward) {
        if (node.isScrollable()) {
            return node.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && findAndScroll(child, forward)) return true;
        }
        return false;
    }

    public void swipe(int startX, int startY, int endX, int endY) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500));
        dispatchGesture(gestureBuilder.build(), null, null);
    }
}