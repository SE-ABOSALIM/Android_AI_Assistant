package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SystemSpeechRecognizerArchitectureTest {
    @Test
    public void productionRecognizer_doesNotSetExtraAudioSource() throws Exception {
        assertFalse(serviceSource().contains("RecognizerIntent.EXTRA_AUDIO_SOURCE"));
    }

    @Test
    public void productionRecognizer_doesNotCreateRecognizerAudioSource() throws Exception {
        String service = serviceSource();

        assertFalse(service.contains("RecognizerAudioSource.start("));
        assertFalse(service.contains("new RecognizerAudioSource"));
    }

    @Test
    public void productionRecognizer_usesSystemManagedMicrophone() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("SpeechRecognizer.isRecognitionAvailable(this)"));
        assertTrue(service.contains("SpeechRecognizer.createSpeechRecognizer(this)"));
        assertTrue(service.contains("speechRecognizer.startListening(recognizerIntent)"));
        assertFalse(service.contains("RecognizerIntent.EXTRA_AUDIO_SOURCE"));
    }

    @Test
    public void productionRecognizer_doesNotUseCreateOnDeviceSpeechRecognizer() throws Exception {
        assertFalse(serviceSource().contains("createOnDeviceSpeechRecognizer"));
    }

    @Test
    public void productionRecognizer_doesNotDependOnIsOnDeviceRecognitionAvailable() throws Exception {
        assertFalse(serviceSource().contains("isOnDeviceRecognitionAvailable"));
    }

    @Test
    public void recognizerIntent_preservesLanguageConfiguration() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("updateLanguage(selectedLanguage)"));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"tr-TR\""));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"en-US\""));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"ar\""));
    }

    @Test
    public void recognizerIntent_preservesPartialResultsConfiguration() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains(
                "RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM"
        ));
        assertTrue(service.contains("RecognizerIntent.EXTRA_PARTIAL_RESULTS, true"));
        assertTrue(service.contains("RecognizerIntent.EXTRA_MAX_RESULTS, 1"));
    }

    @Test
    public void recognitionRestartScheduler_isUnchanged() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("RESTART_DELAY_FAST_MS = 200"));
        assertTrue(service.contains("RESTART_DELAY_SLOW_MS = 800"));
        assertTrue(service.contains("private void scheduleListeningRestart(int delayMillis)"));
        assertTrue(service.contains("listeningRestartScheduler.schedule(delayMillis)"));
        assertTrue(service.contains("listeningRestartScheduler.cancel()"));
    }

    @Test
    public void recognitionGenerationProtection_isUnchanged() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("createRecognitionListener(long recognitionGeneration)"));
        assertTrue(countOccurrences(
                service,
                "isCurrentRecognitionGeneration(recognitionGeneration)"
        ) >= 6);
    }

    @Test
    public void normalSuccessfulResult_restartTimingIsPreserved() throws Exception {
        String resultsCallback = sourceSection(
                serviceSource(),
                "public void onResults(Bundle results)",
                "public void onPartialResults(Bundle partialResults)"
        );

        assertTrue(resultsCallback.contains("scheduleListeningRestart(RESTART_DELAY_FAST_MS)"));
    }

    @Test
    public void normalError_restartBehaviorIsPreserved() throws Exception {
        String errorCallback = sourceSection(
                serviceSource(),
                "public void onError(int error)",
                "public void onResults(Bundle results)"
        );

        assertTrue(errorCallback.contains("case SpeechRecognizer.ERROR_NO_MATCH"));
        assertTrue(errorCallback.contains("scheduleListeningRestart(RESTART_DELAY_FAST_MS)"));
        assertTrue(errorCallback.contains("case SpeechRecognizer.ERROR_AUDIO"));
        assertTrue(errorCallback.contains("scheduleListeningRestart(RESTART_DELAY_SLOW_MS)"));
    }

    @Test
    public void systemManagedMicrophoneArchitecture_isStillPreserved() throws Exception {
        productionRecognizer_usesSystemManagedMicrophone();
        normalRecognitionLifecycle_doesNotRequireApplicationAudioRecord();
    }

    @Test
    public void terminalCallbackRecovery_isGenerationScopedAndUsesExistingRestartScheduler()
            throws Exception {
        String service = serviceSource();
        String endOfSpeech = sourceSection(
                service,
                "public void onEndOfSpeech()",
                "public void onError(int error)"
        );
        String timeoutHandler = sourceSection(
                service,
                "private void handleTerminalCallbackTimeout(long recognitionGeneration)",
                "private void handleRecognizerReadyWatchdogTimeout()"
        );

        assertTrue(service.contains("TERMINAL_CALLBACK_TIMEOUT_MS = 12000"));
        assertTrue(endOfSpeech.contains("scheduleTerminalCallbackWatchdog(recognitionGeneration)"));
        assertTrue(timeoutHandler.contains(
                "isCurrentRecognitionGeneration(recognitionGeneration)"
        ));
        assertTrue(timeoutHandler.contains("invalidateRecognitionCallbacks()"));
        assertTrue(timeoutHandler.contains("speechRecognizer.cancel()"));
        assertTrue(timeoutHandler.contains("scheduleListeningRestart(RESTART_DELAY_FAST_MS)"));
    }

    @Test
    public void intentionalStopDestroyAndPause_clearTerminalCallbackWatchdog() throws Exception {
        String service = serviceSource();

        assertTrue(sourceSection(
                service,
                "private void destroySpeechRecognizer()",
                "private void startListeningSession()"
        ).contains("clearTerminalCallbackWatchdog()"));
        assertTrue(sourceSection(
                service,
                "public void stopContinuousListening()",
                "private void startCallStateMonitoringIfAllowed()"
        ).contains("clearTerminalCallbackWatchdog()"));
        assertTrue(sourceSection(
                service,
                "private void pauseListeningForPhoneCall()",
                "private void resumeListeningAfterPhoneCall()"
        ).contains("clearTerminalCallbackWatchdog()"));
    }

    @Test
    public void legitimateTerminalPaths_completeOrClearTerminalCallbackWatchdog() throws Exception {
        String service = serviceSource();

        assertTrue(sourceSection(
                service,
                "public void onError(int error)",
                "public void onResults(Bundle results)"
        ).contains("terminalCallbackWatchdog.complete(recognitionGeneration)"));
        assertTrue(sourceSection(
                service,
                "public void onResults(Bundle results)",
                "public void onPartialResults(Bundle partialResults)"
        ).contains("terminalCallbackWatchdog.complete(recognitionGeneration)"));
        assertTrue(sourceSection(
                service,
                "private void finalizeLatestPartialResult()",
                "private Intent buildRecognizerIntentForSession()"
        ).contains("clearTerminalCallbackWatchdog()"));
    }

    @Test
    public void normalRecognitionLifecycle_doesNotRequireApplicationAudioRecord() throws Exception {
        String service = serviceSource();

        assertFalse(service.contains("AudioRecord"));
        assertFalse(service.contains("ParcelFileDescriptor"));
        assertFalse(Files.exists(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/speech/RecognizerAudioSource.java"
        )));
    }

    @Test
    public void privacyPolicy_noLongerClaimsOnDevicePreference() throws Exception {
        String policy = repositoryPrivacyPolicy().toLowerCase();

        assertFalse(policy.contains("prefers on-device"));
        assertFalse(policy.contains("compatibility and availability fallback"));
    }

    @Test
    public void privacyPolicy_describesAndroidConfiguredRecognitionService() throws Exception {
        String policy = repositoryPrivacyPolicy();

        assertTrue(policy.contains("speech-recognition service configured by Android"));
        assertTrue(policy.contains("may process microphone audio on the device or"));
        assertTrue(policy.contains("does not send raw"));
        assertTrue(policy.contains("microphone audio or audio files to the Android AI Assistant backend"));
        assertTrue(policy.contains("recognized text from Android"));
        assertTrue(policy.contains("does not assume one universal speech-recognition"));
    }

    @Test
    public void bundledPrivacyPolicy_matchesRepositoryPolicy() throws Exception {
        assertEquals(normalized(repositoryPrivacyPolicy()), normalized(bundledPrivacyPolicy()));
    }

    private String serviceSource() throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/MyAccessibilityService.java"
        ));
    }

    private String repositoryPrivacyPolicy() throws IOException {
        return readUtf8(repositoryRoot().resolve("docs/privacy-policy.md"));
    }

    private String bundledPrivacyPolicy() throws IOException {
        return readUtf8(appRoot().resolve("src/main/res/raw/privacy_policy.md"));
    }

    private int countOccurrences(String source, String target) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = source.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }
        return count;
    }

    private String sourceSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
    }

    private String normalized(String source) {
        return source.replace("\r\n", "\n").trim();
    }

    private String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        return appRoot().getParent().getParent().getParent();
    }

    private Path appRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
    }
}
