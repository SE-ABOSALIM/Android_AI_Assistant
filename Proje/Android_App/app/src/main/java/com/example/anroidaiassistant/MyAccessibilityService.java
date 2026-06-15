package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.selection.GridCommandParser;
import com.example.anroidaiassistant.selection.SelectionNumberParser;
import com.example.anroidaiassistant.speech.RecognizerAudioSource;
import com.example.anroidaiassistant.telephony.CallStateMonitor;
import com.example.anroidaiassistant.ui.overlay.ListeningOverlayController;
import com.example.anroidaiassistant.ui.overlay.ClickTargetOverlayController;
import com.example.anroidaiassistant.ui.overlay.GridOverlayController;
import com.example.anroidaiassistant.ui.overlay.SelectionOverlayController;
import com.example.anroidaiassistant.ui.overlay.UninstallConfirmationOverlayController;
import com.example.anroidaiassistant.util.DeviceIdentity;
import com.example.anroidaiassistant.util.TextNormalizer;
import com.example.anroidaiassistant.accessibility.AccessibilityActionController;
import com.example.anroidaiassistant.accessibility.CameraCaptureController;
import com.example.anroidaiassistant.accessibility.DevicePowerController;
import com.example.anroidaiassistant.accessibility.GridController;
import com.example.anroidaiassistant.accessibility.click.ClickItemController;
import com.example.anroidaiassistant.accessibility.GestureController;
import com.example.anroidaiassistant.accessibility.QuickSettingsTileController;
import com.example.anroidaiassistant.accessibility.SearchInputController;
import com.example.anroidaiassistant.accessibility.ScreenLabelsController;
import com.example.anroidaiassistant.accessibility.SystemUninstallController;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
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
import android.view.inputmethod.InputMethodManager;
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
    private static final int PARTIAL_RESULT_FINALIZE_DELAY_MS = 1200;
    private static final int CLOSE_APP_BACK_RETRY_DELAY_MS = 450;
    private static final int CLOSE_APP_MAX_BACK_ATTEMPTS = 5;
    private static final int[] BASE_RECOGNIZER_SOUND_STREAMS_TO_MUTE = {
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF
    };

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private boolean isSpellAppMode = false;
    private boolean isNumberSelectionMode = false;
    private boolean isConfirmationSelectionMode = false;
    private boolean isRecognitionSessionActive = false;
    private boolean isRecognizerReadyForSpeech = false;
    private boolean areRecognizerSoundsMuted = false;
    private boolean isPausedForPhoneCall = false;
    private boolean externalRecognizerAudioSourceEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    private boolean hasHandledCurrentRecognitionResult = false;
    private String latestPartialRecognitionText;
    private final Map<Integer, Boolean> streamMuteStateBeforeRecognizer = new HashMap<>();
    private RecognizerAudioSource activeRecognizerAudioSource;
    private final List<NumberedChoice> numberSelectionChoices = new ArrayList<>();
    private final SelectionNumberParser selectionNumberParser = new SelectionNumberParser();
    private final GridCommandParser gridCommandParser = new GridCommandParser();
    private NumberSelectionCallback numberSelectionCallback;
    private String numberSelectionTitle;
    private String numberSelectionHint;
    private String uninstallConfirmationAppName;
    private Drawable uninstallConfirmationIcon;
    private String uninstallConfirmationQuestion;
    private String uninstallConfirmationYesText;
    private String uninstallConfirmationNoText;
    private String selectedLanguage = "TR";
    
    private ApiService apiService;
    private Call<AppCatalogResponse> appCatalogSyncCall;
    private CallStateMonitor callStateMonitor;
    private PendingPrediction pendingPrediction;
    private CommandExecutor commandExecutor;
    private AudioManager audioManager;
    private AccessibilityActionController accessibilityActionController;
    private GestureController gestureController;
    private CameraCaptureController cameraCaptureController;
    private DevicePowerController devicePowerController;
    private GridController gridController;
    private ClickItemController clickItemController;
    private QuickSettingsTileController quickSettingsTileController;
    private SearchInputController searchInputController;
    private ScreenLabelsController screenLabelsController;
    private SystemUninstallController systemUninstallController;

    private WindowManager windowManager;
    private ListeningOverlayController listeningOverlayController;
    private ClickTargetOverlayController clickTargetOverlayController;
    private GridOverlayController gridOverlayController;
    private SelectionOverlayController selectionOverlayController;
    private UninstallConfirmationOverlayController uninstallConfirmationOverlayController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable restartListeningRunnable = this::startListeningSession;
    private final Runnable partialResultFinalizeRunnable = this::finalizeLatestPartialResult;

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        selectedLanguage = AssistantSettings.getLanguage(this);
        
        apiService = RetrofitClient.getClient().create(ApiService.class);
        callStateMonitor = new CallStateMonitor(this, this::onCallActiveChanged);
        commandExecutor = new CommandExecutor(this);
        accessibilityActionController = new AccessibilityActionController(this);
        gestureController = new GestureController(this);
        cameraCaptureController = new CameraCaptureController(this, mainHandler, gestureController);
        devicePowerController = new DevicePowerController(this, mainHandler);
        clickItemController = new ClickItemController(this, gestureController);
        quickSettingsTileController = new QuickSettingsTileController(this, mainHandler, gestureController);
        searchInputController = new SearchInputController(this, mainHandler, gestureController);
        screenLabelsController = new ScreenLabelsController(this, gestureController);
        systemUninstallController = new SystemUninstallController(this, mainHandler);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        listeningOverlayController = new ListeningOverlayController(this, windowManager);
        clickTargetOverlayController = new ClickTargetOverlayController(this, windowManager);
        gridOverlayController = new GridOverlayController(this, windowManager);
        gridController = new GridController(gridOverlayController, gestureController);
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
        uninstallConfirmationOverlayController = new UninstallConfirmationOverlayController(this, windowManager, new UninstallConfirmationOverlayController.Listener() {
            @Override
            public void onConfirmRequested() {
                completeNumberSelection(0);
            }

            @Override
            public void onRejectRequested() {
                completeNumberSelection(1);
            }

            @Override
            public void onConfirmationDismissed() {
                cancelNumberSelection();
            }
        });
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        setupSpeechRecognizer();
        Toast.makeText(this, R.string.accessibility_service_connected, Toast.LENGTH_SHORT).show();
    }

    private void setupSpeechRecognizer() {
        destroySpeechRecognizer();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.speech_recognizer_unavailable, Toast.LENGTH_LONG).show();
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
                isRecognizerReadyForSpeech = true;
                showRecognizerReadyState();
            }
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {
                isRecognizerReadyForSpeech = false;
            }

            @Override
            public void onError(int error) {
                if (hasHandledCurrentRecognitionResult) {
                    return;
                }
                if ((error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        || error == SpeechRecognizer.ERROR_CLIENT)
                        && latestPartialRecognitionText != null
                        && !latestPartialRecognitionText.trim().isEmpty()) {
                    finalizeLatestPartialResult();
                    return;
                }
                clearPartialResultFallback();
                boolean wasUsingExternalAudioSource = activeRecognizerAudioSource != null;
                stopActiveRecognizerAudioSource();
                isRecognitionSessionActive = false;
                isRecognizerReadyForSpeech = false;
                if (isPausedForPhoneCall) {
                    return;
                }
                if (!isListening) {
                    return;
                }

                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO:
                        if (wasUsingExternalAudioSource) {
                            externalRecognizerAudioSourceEnabled = false;
                        }
                        setupSpeechRecognizer();
                        scheduleListeningRestart(RESTART_DELAY_SLOW_MS);
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    case SpeechRecognizer.ERROR_CLIENT:
                        showRecognizerPreparingState();
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
                if (hasHandledCurrentRecognitionResult) {
                    return;
                }
                clearPartialResultFallback();
                stopActiveRecognizerAudioSource();
                isRecognitionSessionActive = false;
                isRecognizerReadyForSpeech = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    hasHandledCurrentRecognitionResult = true;
                    handleRecognizedSpeech(matches.get(0), matches);
                }
                if (isListening) {
                    scheduleListeningRestart(RESTART_DELAY_FAST_MS);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    schedulePartialResultFallback(spokenText);
                    if (!isNumberSelectionMode && !isGridActive()) {
                        updateOverlayText("Hearing: " + spokenText);
                    }
                }
            }
            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void destroySpeechRecognizer() {
        mainHandler.removeCallbacks(restartListeningRunnable);
        clearPartialResultFallback();
        stopActiveRecognizerAudioSource();
        isRecognitionSessionActive = false;
        isRecognizerReadyForSpeech = false;
        hasHandledCurrentRecognitionResult = false;

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
        clearPartialResultFallback();
        if (!isListening || speechRecognizer == null || isRecognitionSessionActive) {
            return;
        }
        if (isPausedForPhoneCall) {
            return;
        }

        try {
            isRecognitionSessionActive = true;
            isRecognizerReadyForSpeech = false;
            hasHandledCurrentRecognitionResult = false;
            showRecognizerPreparingState();
            setRecognizerSoundsMuted(true);
            Intent recognizerIntent = buildRecognizerIntentForSession();
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception ignored) {
            stopActiveRecognizerAudioSource();
            isRecognitionSessionActive = false;
            isRecognizerReadyForSpeech = false;
            setupSpeechRecognizer();
            scheduleListeningRestart(RESTART_DELAY_SLOW_MS);
        }
    }

    private void handleRecognizedSpeech(String spokenText, List<String> alternatives) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            return;
        }

        if (isGridActive() && handleGridSpeech(spokenText)) {
            return;
        }

        if (isNumberSelectionMode) {
            handleNumberSelectionResult(spokenText);
            return;
        }

        if (consumeSpellAppMode()) {
            updateOverlayText("Input: " + spokenText);
            if (commandExecutor != null) {
                commandExecutor.handleSpelledAppCandidate(spokenText);
            }
            return;
        }

        updateOverlayText("Input: " + spokenText);
        sendPredictionRequest(spokenText, alternatives);
    }

    private void schedulePartialResultFallback(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty() || hasHandledCurrentRecognitionResult) {
            return;
        }

        latestPartialRecognitionText = spokenText;
        mainHandler.removeCallbacks(partialResultFinalizeRunnable);
        mainHandler.postDelayed(partialResultFinalizeRunnable, PARTIAL_RESULT_FINALIZE_DELAY_MS);
    }

    private void clearPartialResultFallback() {
        mainHandler.removeCallbacks(partialResultFinalizeRunnable);
        latestPartialRecognitionText = null;
    }

    private void finalizeLatestPartialResult() {
        String spokenText = latestPartialRecognitionText;
        clearPartialResultFallback();

        if (spokenText == null || spokenText.trim().isEmpty()) {
            return;
        }
        if (!isListening || isPausedForPhoneCall || !isRecognitionSessionActive || hasHandledCurrentRecognitionResult) {
            return;
        }

        hasHandledCurrentRecognitionResult = true;
        stopActiveRecognizerAudioSource();
        isRecognitionSessionActive = false;
        isRecognizerReadyForSpeech = false;

        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }

        ArrayList<String> alternatives = new ArrayList<>();
        alternatives.add(spokenText);
        handleRecognizedSpeech(spokenText, alternatives);

        if (isListening) {
            scheduleListeningRestart(RESTART_DELAY_FAST_MS);
        }
    }

    private Intent buildRecognizerIntentForSession() {
        Intent recognizerIntent = new Intent(speechRecognizerIntent);
        stopActiveRecognizerAudioSource();

        if (!externalRecognizerAudioSourceEnabled) {
            return recognizerIntent;
        }

        RecognizerAudioSource audioSource = RecognizerAudioSource.start(this);
        if (audioSource == null) {
            return recognizerIntent;
        }

        activeRecognizerAudioSource = audioSource;
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource.getReadDescriptor());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, RecognizerAudioSource.CHANNEL_COUNT);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, RecognizerAudioSource.AUDIO_ENCODING);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RecognizerAudioSource.SAMPLE_RATE_HZ);
        return recognizerIntent;
    }

    private void stopActiveRecognizerAudioSource() {
        if (activeRecognizerAudioSource == null) {
            return;
        }
        activeRecognizerAudioSource.close();
        activeRecognizerAudioSource = null;
    }

    private void scheduleListeningRestart(int delayMillis) {
        if (!isListening || isPausedForPhoneCall) {
            return;
        }

        mainHandler.removeCallbacks(restartListeningRunnable);
        mainHandler.postDelayed(restartListeningRunnable, delayMillis);
    }

    private List<Integer> getRecognizerSoundStreamsToMute() {
        List<Integer> streams = new ArrayList<>();
        for (int stream : BASE_RECOGNIZER_SOUND_STREAMS_TO_MUTE) {
            streams.add(stream);
        }
        return streams;
    }

    private void setRecognizerSoundsMuted(boolean muted) {
        if (audioManager == null) {
            return;
        }
        if (muted && audioManager.isMusicActive()) {
            return;
        }
        if (!muted && !areRecognizerSoundsMuted) {
            return;
        }

        boolean captureOriginalState = muted && !areRecognizerSoundsMuted;
        if (captureOriginalState) {
            streamMuteStateBeforeRecognizer.clear();
        }

        for (int stream : getRecognizerSoundStreamsToMute()) {
            try {
                boolean wasMuted = audioManager.isStreamMute(stream);
                if (muted) {
                    if (captureOriginalState) {
                        streamMuteStateBeforeRecognizer.put(stream, wasMuted);
                    }
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

    public String getSelectedLanguage() {
        return selectedLanguage;
    }

    public void startContinuousListening() {
        mainHandler.removeCallbacks(restartListeningRunnable);
        isListening = true;
        startCallStateMonitoringIfAllowed();
        if (callStateMonitor != null && callStateMonitor.isCallActive()) {
            pauseListeningForPhoneCall();
            return;
        }
        setRecognizerSoundsMuted(true);
        showOverlay();
        showRecognizerPreparingState();
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }
        startListeningSession();
    }

    public void stopContinuousListening() {
        isListening = false;
        isPausedForPhoneCall = false;
        isSpellAppMode = false;
        clearNumberSelection();
        hideGrid();
        cancelAppCatalogSyncIfNeeded();
        mainHandler.removeCallbacks(restartListeningRunnable);
        clearPartialResultFallback();
        stopActiveRecognizerAudioSource();
        setRecognizerSoundsMuted(false);
        isRecognitionSessionActive = false;
        isRecognizerReadyForSpeech = false;
        hasHandledCurrentRecognitionResult = false;
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

    private void startCallStateMonitoringIfAllowed() {
        if (callStateMonitor != null) {
            callStateMonitor.start();
        }
    }

    private void onCallActiveChanged(boolean active) {
        mainHandler.post(() -> {
            if (active) {
                pauseListeningForPhoneCall();
            } else {
                resumeListeningAfterPhoneCall();
            }
        });
    }

    private void pauseListeningForPhoneCall() {
        if (!isListening || isPausedForPhoneCall) {
            return;
        }

        isPausedForPhoneCall = true;
        isSpellAppMode = false;
        clearNumberSelection(false);
        hideGrid();
        mainHandler.removeCallbacks(restartListeningRunnable);
        clearPartialResultFallback();
        stopActiveRecognizerAudioSource();
        isRecognitionSessionActive = false;
        isRecognizerReadyForSpeech = false;
        hasHandledCurrentRecognitionResult = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
        setRecognizerSoundsMuted(false);
        hideOverlay();
    }

    private void resumeListeningAfterPhoneCall() {
        if (!isPausedForPhoneCall) {
            return;
        }

        isPausedForPhoneCall = false;
        if (!isListening) {
            return;
        }

        setRecognizerSoundsMuted(true);
        showOverlay();
        showRecognizerPreparingState();
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }
        startListeningSession();
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
            selectionOverlayController.show(numberSelectionTitle, numberSelectionChoices, numberSelectionHint);
        }
    }

    private void hideSelectionWindow() {
        if (selectionOverlayController != null) {
            selectionOverlayController.hide();
        }
    }

    private void showClickTargetMarkers() {
        if (clickTargetOverlayController != null) {
            List<ClickTargetChoice> choices = new ArrayList<>();
            for (NumberedChoice choice : numberSelectionChoices) {
                if (choice instanceof ClickTargetChoice) {
                    choices.add((ClickTargetChoice) choice);
                }
            }
            clickTargetOverlayController.show(choices);
        }
    }

    private void hideClickTargetMarkers() {
        if (clickTargetOverlayController != null) {
            clickTargetOverlayController.hide();
        }
    }

    private void hideGrid() {
        if (gridController != null) {
            gridController.hide();
        }
    }

    private void showUninstallConfirmationWindow() {
        if (uninstallConfirmationOverlayController != null) {
            uninstallConfirmationOverlayController.show(
                    uninstallConfirmationAppName,
                    uninstallConfirmationIcon,
                    uninstallConfirmationQuestion,
                    uninstallConfirmationYesText,
                    uninstallConfirmationNoText,
                    numberSelectionHint
            );
        }
    }

    private void hideUninstallConfirmationWindow() {
        if (uninstallConfirmationOverlayController != null) {
            uninstallConfirmationOverlayController.hide();
        }
    }

    private void updateOverlayText(String text) {
        if (listeningOverlayController != null) {
            listeningOverlayController.updateText(text);
        }
    }

    private void showRecognizerPreparingState() {
        if (shouldShowRecognizerStateInOverlay()) {
            updateOverlayText(getString(R.string.overlay_preparing));
        }
    }

    private void showRecognizerReadyState() {
        if (shouldShowRecognizerStateInOverlay()) {
            updateOverlayText(getString(R.string.overlay_listening));
        }
    }

    private void showCurrentRecognizerState() {
        if (isRecognizerReadyForSpeech) {
            showRecognizerReadyState();
        } else {
            showRecognizerPreparingState();
        }
    }

    private boolean shouldShowRecognizerStateInOverlay() {
        return !isNumberSelectionMode && !isGridActive();
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

        String sessionId = AssistantSession.getOrCreateSessionId();

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
                DeviceIdentity.getDeviceId(this),
                alternatives,
                hasSearchInputAvailable()
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
                    showRequestError(getString(R.string.backend_no_response));
                }
            }
            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                Log.e(TAG, "Prediction request failed", t);
                showRequestError(getString(R.string.backend_unavailable));
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
        if (isGridActive() && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                hideGrid();
                if (isListening) {
                    showOverlay();
                    showCurrentRecognizerState();
                }
            }
            return true;
        }

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
        isPausedForPhoneCall = false;
        isSpellAppMode = false;
        clearNumberSelection();
        hideGrid();
        cancelAppCatalogSyncIfNeeded();
        closeCurrentBackendSession();
        hideOverlay();
        setRecognizerSoundsMuted(false);
        destroySpeechRecognizer();
        if (callStateMonitor != null) {
            callStateMonitor.stop();
        }
        instance = null;
    }

    public boolean isContinuousListeningActive() {
        return isListening;
    }

    public boolean playMediaPlayback() {
        return dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    private boolean dispatchMediaKey(int keyCode) {
        if (audioManager == null) {
            return false;
        }

        try {
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
        hideGrid();
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
        startSelection(title, choices, callback, false);
    }

    public void startNumberSelection(
            String title,
            List<NumberedChoice> choices,
            NumberSelectionCallback callback,
            String hint
    ) {
        startSelection(title, choices, callback, false, hint);
    }

    public void startClickTargetSelection(
            List<ClickTargetChoice> choices,
            NumberSelectionCallback callback,
            String hint
    ) {
        if (choices == null || choices.isEmpty() || callback == null) {
            return;
        }

        isSpellAppMode = false;
        hideGrid();
        isNumberSelectionMode = true;
        isConfirmationSelectionMode = false;
        numberSelectionTitle = null;
        numberSelectionHint = TextNormalizer.hasText(hint)
                ? hint
                : "Istediginiz ogenin numarasini soyleyin.";
        numberSelectionCallback = callback;
        numberSelectionChoices.clear();
        numberSelectionChoices.addAll(choices);
        hideOverlay();
        hideSelectionWindow();
        hideUninstallConfirmationWindow();
        showClickTargetMarkers();
        restartListeningForNumberSelection();
    }

    public void startConfirmationSelection(
            String title,
            List<NumberedChoice> choices,
            NumberSelectionCallback callback
    ) {
        startSelection(title, choices, callback, true);
    }

    public void startUninstallConfirmation(
            String appName,
            Drawable appIcon,
            String question,
            String yesText,
            String noText,
            String hint,
            NumberSelectionCallback callback
    ) {
        startActionConfirmation(appName, appIcon, question, yesText, noText, hint, callback);
    }

    public void startActionConfirmation(
            String actionName,
            Drawable actionIcon,
            String question,
            String yesText,
            String noText,
            String hint,
            NumberSelectionCallback callback
    ) {
        if (callback == null) {
            return;
        }

        List<NumberedChoice> choices = new ArrayList<>();
        choices.add(new NumberedChoice(yesText, actionName, actionIcon));
        choices.add(new NumberedChoice(noText, actionName, actionIcon));

        isSpellAppMode = false;
        hideGrid();
        isNumberSelectionMode = true;
        isConfirmationSelectionMode = true;
        numberSelectionTitle = question;
        numberSelectionHint = hint;
        numberSelectionCallback = callback;
        numberSelectionChoices.clear();
        numberSelectionChoices.addAll(choices);
        uninstallConfirmationAppName = actionName;
        uninstallConfirmationIcon = actionIcon;
        uninstallConfirmationQuestion = question;
        uninstallConfirmationYesText = yesText;
        uninstallConfirmationNoText = noText;
        hideOverlay();
        hideSelectionWindow();
        showUninstallConfirmationWindow();
        restartListeningForNumberSelection();
    }

    private void startSelection(
            String title,
            List<NumberedChoice> choices,
            NumberSelectionCallback callback,
            boolean confirmationMode
    ) {
        startSelection(title, choices, callback, confirmationMode, null);
    }

    private void startSelection(
            String title,
            List<NumberedChoice> choices,
            NumberSelectionCallback callback,
            boolean confirmationMode,
            String customHint
    ) {
        if (choices == null || choices.isEmpty() || callback == null) {
            return;
        }

        isSpellAppMode = false;
        hideGrid();
        isNumberSelectionMode = true;
        isConfirmationSelectionMode = confirmationMode;
        numberSelectionTitle = title;
        if (TextNormalizer.hasText(customHint)) {
            numberSelectionHint = customHint;
        } else if (confirmationMode) {
            numberSelectionHint = confirmationSelectionHint();
        } else {
            numberSelectionHint = "Birden cok secenek bulundu. Hangisini isterseniz numarasini soyleyin.";
        }
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
        isRecognizerReadyForSpeech = false;
        mainHandler.postDelayed(restartListeningRunnable, RESTART_DELAY_FAST_MS);
    }

    private void handleNumberSelectionResult(String spokenText) {
        if (isConfirmationSelectionMode) {
            Integer confirmationIndex = selectionNumberParser.parseConfirmationSelection(spokenText);
            if (confirmationIndex != null && confirmationIndex < numberSelectionChoices.size()) {
                completeNumberSelection(confirmationIndex);
                return;
            }
        }

        Integer selectedIndex = selectionNumberParser.parseSelectionNumber(spokenText, numberSelectionChoices.size());
        if (selectedIndex == null) {
            if (selectionNumberParser.isCancelSelection(spokenText)) {
                cancelNumberSelection();
                return;
            }

            Toast.makeText(this, R.string.selection_number_only, Toast.LENGTH_SHORT).show();
            return;
        }

        completeNumberSelection(selectedIndex);
    }

    private boolean handleGridSpeech(String spokenText) {
        String gridAction = gridCommandParser.parseAction(spokenText);
        if (TextNormalizer.hasText(gridAction)) {
            handleGridAction(gridAction);
            return true;
        }

        if (!gridCommandParser.isCellSelectionText(spokenText)) {
            return false;
        }

        if (gridController == null) {
            return true;
        }

        Integer selectedIndex = selectionNumberParser.parseSelectionNumber(
                spokenText,
                gridController.getCellCount()
        );
        if (selectedIndex == null) {
            Toast.makeText(this, R.string.grid_number_required, Toast.LENGTH_SHORT).show();
            return true;
        }

        String gridGestureAction = gridCommandParser.parseCellGestureAction(spokenText);
        if (gridController.performCellGesture(selectedIndex, gridGestureAction)) {
            return true;
        }

        Toast.makeText(this, R.string.grid_cell_tap_failed, Toast.LENGTH_SHORT).show();
        return true;
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
        boolean clickTargetSelection = isClickTargetSelectionActive();
        clearNumberSelection(!clickTargetSelection);
        if (callback != null) {
            callback.onSelected(selectedIndex);
        }
        if (clickTargetSelection && isListening) {
            showOverlay();
            showCurrentRecognizerState();
        }
    }

    private void clearNumberSelection() {
        clearNumberSelection(true);
    }

    private void clearNumberSelection(boolean restoreListeningOverlay) {
        isNumberSelectionMode = false;
        isConfirmationSelectionMode = false;
        numberSelectionTitle = null;
        numberSelectionHint = null;
        numberSelectionCallback = null;
        numberSelectionChoices.clear();
        uninstallConfirmationAppName = null;
        uninstallConfirmationIcon = null;
        uninstallConfirmationQuestion = null;
        uninstallConfirmationYesText = null;
        uninstallConfirmationNoText = null;
        hideSelectionWindow();
        hideClickTargetMarkers();
        hideUninstallConfirmationWindow();
        if (restoreListeningOverlay && isListening) {
            showOverlay();
            showCurrentRecognizerState();
        }
    }

    private boolean isClickTargetSelectionActive() {
        return !numberSelectionChoices.isEmpty()
                && numberSelectionChoices.get(0) instanceof ClickTargetChoice;
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

    public static final class ClickTargetChoice extends NumberedChoice {
        public final Rect bounds;

        public ClickTargetChoice(String title, String subtitle, Rect bounds) {
            super(title, subtitle);
            this.bounds = bounds == null ? new Rect() : new Rect(bounds);
        }
    }

    public interface NumberSelectionCallback {
        void onSelected(int selectedIndex);
        void onCancelled();
    }

    private String confirmationSelectionHint() {
        if ("EN".equalsIgnoreCase(selectedLanguage)) {
            return "Say yes or no.";
        }
        if ("AR".equalsIgnoreCase(selectedLanguage)) {
            return "\u0642\u0644 \u0646\u0639\u0645 \u0627\u0648 \u0644\u0627.";
        }
        return "Evet veya hayir deyin.";
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
        if (!TextNormalizer.hasText(initialPackageName)) {
            performBack();
            return;
        }

        performCloseAppBackAttempt(initialPackageName, 1);
    }

    private void performCloseAppBackAttempt(String initialPackageName, int attempt) {
        performBack();
        mainHandler.postDelayed(() -> {
            String currentPackageName = getActivePackageName();
            if (!initialPackageName.equals(currentPackageName)) {
                return;
            }
            if (attempt >= CLOSE_APP_MAX_BACK_ATTEMPTS) {
                showFeedback("App could not be closed.");
                return;
            }
            performCloseAppBackAttempt(initialPackageName, attempt + 1);
        }, CLOSE_APP_BACK_RETRY_DELAY_MS);
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

    public boolean performScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        if (accessibilityActionController != null) {
            return accessibilityActionController.performScreenshot();
        }
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    public void clickNodeByText(String text) {
        if (accessibilityActionController != null) {
            accessibilityActionController.clickNodeByText(text);
        }
    }

    public boolean capturePhoto() {
        return cameraCaptureController != null && cameraCaptureController.capturePhoto();
    }

    public boolean capturePhoto(String camera) {
        return cameraCaptureController != null && cameraCaptureController.capturePhoto(camera);
    }

    public boolean scroll(String direction) {
        return gestureController != null && gestureController.scroll(direction);
    }

    public boolean swipe(String direction) {
        return gestureController != null && gestureController.swipe(direction);
    }

    public boolean doubleTapCenter() {
        return gestureController != null && gestureController.doubleTapCenter();
    }

    public boolean longPressCenter() {
        return gestureController != null && gestureController.longPressCenter();
    }

    public boolean clickItem(String targetText, String position) {
        return clickItemController != null && clickItemController.clickItem(targetText, position);
    }

    public boolean showScreenLabels() {
        return screenLabelsController != null && screenLabelsController.showLabels();
    }

    public boolean selectNumberedChoice(int oneBasedNumber) {
        if (!isNumberSelectionMode) {
            return false;
        }

        int selectedIndex = oneBasedNumber - 1;
        if (selectedIndex < 0 || selectedIndex >= numberSelectionChoices.size()) {
            return false;
        }

        completeNumberSelection(selectedIndex);
        return true;
    }

    public boolean performDevicePowerAction(DevicePowerController.Action action) {
        hideOverlay();
        return devicePowerController != null && devicePowerController.perform(action);
    }

    public void handleGridAction(String action) {
        if (gridController == null) {
            showFeedback("Grid is unavailable");
            return;
        }

        gridController.handleAction(action);
        if (gridController.isActive()) {
            hideOverlay();
            return;
        }

        if (isListening) {
            showOverlay();
            showCurrentRecognizerState();
        }
    }

    public boolean isGridActive() {
        return gridController != null && gridController.isActive();
    }

    public boolean setSoftKeyboardVisible(boolean visible) {
        getSoftKeyboardController().setShowMode(visible ? SHOW_MODE_AUTO : SHOW_MODE_HIDDEN);
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (visible && inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
        return true;
    }

    public boolean setQuickSettingState(String intent, String state) {
        return quickSettingsTileController != null
                && quickSettingsTileController.setTileState(intent, state, this::showFeedback);
    }

    public boolean performSearchQuery(String query) {
        return searchInputController != null && searchInputController.performSearch(query);
    }

    public boolean pressKeyboardAction(String actionText) {
        return searchInputController != null && searchInputController.pressKeyboardAction(actionText);
    }

    public boolean hasSearchInputAvailable() {
        return searchInputController != null && searchInputController.hasSearchInputAvailable();
    }

    public boolean writeTextToFocusedInput(String text) {
        return searchInputController != null && searchInputController.writeText(text);
    }

    public boolean clearFocusedInputText() {
        return searchInputController != null && searchInputController.clearText();
    }

    public void confirmSystemUninstallDialog(String packageName, String label) {
        if (systemUninstallController != null) {
            systemUninstallController.confirmNextSystemUninstallDialog(packageName, label);
        }
    }

    private String getActivePackageName() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null || rootNode.getPackageName() == null) {
            return null;
        }
        return rootNode.getPackageName().toString();
    }
}
