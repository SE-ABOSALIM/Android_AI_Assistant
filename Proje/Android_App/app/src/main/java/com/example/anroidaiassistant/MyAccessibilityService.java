package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.selection.SelectionNumberParser;
import com.example.anroidaiassistant.ui.ListeningOverlayController;
import com.example.anroidaiassistant.ui.SelectionOverlayController;
import com.example.anroidaiassistant.accessibility.AccessibilityActionController;
import com.example.anroidaiassistant.accessibility.CameraCaptureController;
import com.example.anroidaiassistant.accessibility.GestureController;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static MyAccessibilityService instance;
    private static final int RESTART_DELAY_FAST_MS = 200;
    private static final int RESTART_DELAY_SLOW_MS = 800;
    private static final int CLOSE_APP_SECOND_BACK_DELAY_MS = 500;
    private static final int[] STREAMS_TO_MUTE = {
            AudioManager.STREAM_SYSTEM
    };

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private boolean isSpellAppMode = false;
    private boolean isNumberSelectionMode = false;
    private boolean isRecognitionSessionActive = false;
    private boolean areRecognizerSoundsMuted = false;
    private final Map<Integer, Boolean> streamMuteStateBeforeRecognizer = new HashMap<>();
    private final List<NumberedChoice> numberSelectionChoices = new ArrayList<>();
    private final SelectionNumberParser selectionNumberParser = new SelectionNumberParser();
    private NumberSelectionCallback numberSelectionCallback;
    private String numberSelectionTitle;
    private String selectedLanguage = "TR";
    
    private ApiService apiService;
    private Call<AppCatalogResponse> appCatalogSyncCall;
    private PendingPrediction pendingPrediction;
    private CommandExecutor commandExecutor;
    private AudioManager audioManager;
    private AccessibilityActionController accessibilityActionController;
    private GestureController gestureController;
    private CameraCaptureController cameraCaptureController;

    private WindowManager windowManager;
    private ListeningOverlayController listeningOverlayController;
    private SelectionOverlayController selectionOverlayController;
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
        accessibilityActionController = new AccessibilityActionController(this);
        gestureController = new GestureController(this);
        cameraCaptureController = new CameraCaptureController(this, mainHandler, gestureController);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        listeningOverlayController = new ListeningOverlayController(this, windowManager);
        selectionOverlayController = new SelectionOverlayController(this, windowManager, new SelectionOverlayController.Listener() {
            @Override
            public void onChoiceSelected(int selectedIndex) {
                completeNumberSelection(selectedIndex);
            }

            @Override
            public void onSelectionCancelled() {
                cancelNumberSelection();
            }
        });
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
                if (!isNumberSelectionMode) {
                    updateOverlayText("Listening...");
                }
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
                        if (!isNumberSelectionMode) {
                            updateOverlayText("Listening...");
                        }
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
                    if (isNumberSelectionMode) {
                        handleNumberSelectionResult(spokenText);
                    } else if (consumeSpellAppMode()) {
                        updateOverlayText("Input: " + spokenText);
                        if (commandExecutor != null) {
                            commandExecutor.handleSpelledAppCandidate(spokenText);
                        }
                    } else {
                        updateOverlayText("Input: " + spokenText);
                        sendPredictionRequest(spokenText, matches);
                    }
                }
                if (isListening) {
                    scheduleListeningRestart(RESTART_DELAY_FAST_MS);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (isNumberSelectionMode) {
                    return;
                }
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

        if (muted) {
            streamMuteStateBeforeRecognizer.clear();
        }

        for (int stream : STREAMS_TO_MUTE) {
            try {
                boolean wasMuted = audioManager.isStreamMute(stream);
                if (muted) {
                    streamMuteStateBeforeRecognizer.put(stream, wasMuted);
                    if (wasMuted) {
                        continue;
                    }
                } else if (Boolean.TRUE.equals(streamMuteStateBeforeRecognizer.get(stream))) {
                    continue;
                }

                audioManager.adjustStreamVolume(
                        stream,
                        muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                        0
                );
            } catch (Exception ignored) {}
        }

        areRecognizerSoundsMuted = muted;
        if (!muted) {
            streamMuteStateBeforeRecognizer.clear();
        }
    }

    public void updateLanguage(String lang) {
        this.selectedLanguage = lang == null || lang.trim().isEmpty() ? "TR" : lang.trim();
        if (speechRecognizerIntent == null) {
            return;
        }

        if ("TR".equals(selectedLanguage)) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        } else if ("EN".equals(selectedLanguage)) {
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        } else if ("AR".equals(selectedLanguage)) {
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
        clearNumberSelection();
        cancelAppCatalogSyncIfNeeded();
        closeCurrentBackendSession();
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
        if (listeningOverlayController != null) {
            listeningOverlayController.show();
        }
    }

    private void hideOverlay() {
        if (listeningOverlayController != null) {
            listeningOverlayController.hide();
        }
    }

    private void showSelectionWindow() {
        if (selectionOverlayController != null) {
            selectionOverlayController.show(numberSelectionTitle, numberSelectionChoices);
        }
    }

    private void hideSelectionWindow() {
        if (selectionOverlayController != null) {
            selectionOverlayController.hide();
        }
    }

    private void updateOverlayText(String text) {
        if (listeningOverlayController != null) {
            listeningOverlayController.updateText(text);
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
        sendPredictionRequest(text, null);
    }

    private void sendPredictionRequest(String text, List<String> alternatives) {
        if (AssistantSession.isCatalogReadyForLanguage(selectedLanguage)) {
            sendPredictionRequestWithCatalog(text, alternatives);
            return;
        }

        pendingPrediction = new PendingPrediction(text, alternatives);
        if (appCatalogSyncCall != null) {
            updateOverlayText("Uygulama listesi gonderiliyor...");
            return;
        }

        String previousSessionId = AssistantSession.getSessionId();
        String sessionId = AssistantSession.startNewSession();
        if (previousSessionId != null) {
            AppCatalogSyncer.closeSession(apiService, previousSessionId);
        }

        updateOverlayText("Uygulama listesi gonderiliyor...");
        appCatalogSyncCall = AppCatalogSyncer.syncInstalledApps(
                this,
                apiService,
                sessionId,
                selectedLanguage,
                (success, message) -> mainHandler.post(() -> onAppCatalogSynced(sessionId, success, message))
        );

        if (appCatalogSyncCall == null) {
            pendingPrediction = null;
            closeCurrentBackendSession();
            showRequestError("App catalog sync is unavailable");
        }
    }

    private void onAppCatalogSynced(String sessionId, boolean success, String message) {
        if (!sessionId.equals(AssistantSession.getSessionId())) {
            return;
        }

        appCatalogSyncCall = null;
        PendingPrediction prediction = pendingPrediction;
        pendingPrediction = null;

        if (!success) {
            closeCurrentBackendSession();
            showRequestError(message);
            return;
        }

        if (prediction != null) {
            sendPredictionRequestWithCatalog(prediction.text, prediction.alternatives);
        }
    }

    private void sendPredictionRequestWithCatalog(String text, List<String> alternatives) {
        PredictRequest request = new PredictRequest(
                text,
                selectedLanguage,
                AssistantSession.getSessionId(),
                alternatives
        );
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
    protected boolean onKeyEvent(KeyEvent event) {
        if (isNumberSelectionMode
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                cancelNumberSelection();
            }
            return true;
        }

        return super.onKeyEvent(event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
        isSpellAppMode = false;
        clearNumberSelection();
        cancelAppCatalogSyncIfNeeded();
        closeCurrentBackendSession();
        hideOverlay();
        setRecognizerSoundsMuted(false);
        destroySpeechRecognizer();
        instance = null;
    }

    public boolean isContinuousListeningActive() {
        return isListening;
    }

    private void cancelAppCatalogSyncIfNeeded() {
        if (appCatalogSyncCall != null) {
            appCatalogSyncCall.cancel();
            appCatalogSyncCall = null;
        }
        pendingPrediction = null;
    }

    private void closeCurrentBackendSession() {
        String endedSessionId = AssistantSession.endSession();
        AppCatalogSyncer.closeSession(apiService, endedSessionId);
    }

    public void enableSpellAppMode() {
        isSpellAppMode = true;
        clearNumberSelection();
        updateOverlayText("Spell app name...");
    }

    private boolean consumeSpellAppMode() {
        boolean shouldConsume = isSpellAppMode;
        isSpellAppMode = false;
        return shouldConsume;
    }

    public void startNumberSelection(
            String title,
            List<NumberedChoice> choices,
            NumberSelectionCallback callback
    ) {
        if (choices == null || choices.isEmpty() || callback == null) {
            return;
        }

        isSpellAppMode = false;
        isNumberSelectionMode = true;
        numberSelectionTitle = title;
        numberSelectionCallback = callback;
        numberSelectionChoices.clear();
        numberSelectionChoices.addAll(choices);
        hideOverlay();
        showSelectionWindow();
        restartListeningForNumberSelection();
    }

    private void restartListeningForNumberSelection() {
        if (!isListening) {
            return;
        }

        mainHandler.removeCallbacks(restartListeningRunnable);
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
        isRecognitionSessionActive = false;
        mainHandler.postDelayed(restartListeningRunnable, RESTART_DELAY_FAST_MS);
    }

    private void handleNumberSelectionResult(String spokenText) {
        Integer selectedIndex = selectionNumberParser.parseSelectionNumber(spokenText, numberSelectionChoices.size());
        if (selectedIndex == null) {
            if (selectionNumberParser.isCancelSelection(spokenText)) {
                cancelNumberSelection();
                return;
            }

            Toast.makeText(this, "Sadece listedeki numarayi soyle.", Toast.LENGTH_SHORT).show();
            return;
        }

        completeNumberSelection(selectedIndex);
    }

    private void cancelNumberSelection() {
        if (!isNumberSelectionMode) {
            return;
        }

        NumberSelectionCallback callback = numberSelectionCallback;
        clearNumberSelection();
        updateOverlayText("Selection cancelled.");
        if (callback != null) {
            callback.onCancelled();
        }
    }

    private void completeNumberSelection(int selectedIndex) {
        NumberSelectionCallback callback = numberSelectionCallback;
        clearNumberSelection();
        if (callback != null) {
            callback.onSelected(selectedIndex);
        }
    }

    private void clearNumberSelection() {
        isNumberSelectionMode = false;
        numberSelectionTitle = null;
        numberSelectionCallback = null;
        numberSelectionChoices.clear();
        hideSelectionWindow();
        if (isListening) {
            showOverlay();
            updateOverlayText("Listening...");
        }
    }

    public static class NumberedChoice {
        public final String title;
        public final String subtitle;
        public final Drawable icon;

        public NumberedChoice(String title, String subtitle) {
            this(title, subtitle, null);
        }

        public NumberedChoice(String title, String subtitle, Drawable icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    public interface NumberSelectionCallback {
        void onSelected(int selectedIndex);
        void onCancelled();
    }

    private static final class PendingPrediction {
        private final String text;
        private final List<String> alternatives;

        private PendingPrediction(String text, List<String> alternatives) {
            this.text = text;
            this.alternatives = alternatives == null ? null : new ArrayList<>(alternatives);
        }
    }
    public void performHome() {
        if (accessibilityActionController != null) {
            accessibilityActionController.performHome();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void performBack() {
        if (accessibilityActionController != null) {
            accessibilityActionController.performBack();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void performCloseApp() {
        String initialPackageName = getActivePackageName();
        performBack();
        mainHandler.postDelayed(() -> {
            String currentPackageName = getActivePackageName();
            if (initialPackageName == null || initialPackageName.equals(currentPackageName)) {
                performBack();
            }
        }, CLOSE_APP_SECOND_BACK_DELAY_MS);
    }

    public void performRecents() {
        if (accessibilityActionController != null) {
            accessibilityActionController.performRecents();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public void performNotifications() {
        if (accessibilityActionController != null) {
            accessibilityActionController.performNotifications();
            return;
        }
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void clickNodeByText(String text) {
        if (accessibilityActionController != null) {
            accessibilityActionController.clickNodeByText(text);
        }
    }

    public boolean capturePhoto() {
        return cameraCaptureController != null && cameraCaptureController.capturePhoto();
    }

    public boolean scroll(String direction) {
        return gestureController != null && gestureController.scroll(direction);
    }

    public boolean swipe(String direction) {
        return gestureController != null && gestureController.swipe(direction);
    }

    private String getActivePackageName() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null || rootNode.getPackageName() == null) {
            return null;
        }
        return rootNode.getPackageName().toString();
    }
}
