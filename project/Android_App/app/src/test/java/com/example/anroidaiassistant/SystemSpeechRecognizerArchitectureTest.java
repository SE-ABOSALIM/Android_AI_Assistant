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
    public void productionRecognizer_usesSystemSpeechRecognizer() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("SpeechRecognizer.isRecognitionAvailable(this)"));
        assertTrue(service.contains("SpeechRecognizer.createSpeechRecognizer(this)"));
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
    public void recognitionLanguageConfiguration_isPreserved() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("updateLanguage(selectedLanguage)"));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"tr-TR\""));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"en-US\""));
        assertTrue(service.contains("RecognizerIntent.EXTRA_LANGUAGE, \"ar\""));
    }

    @Test
    public void recognitionRestartScheduler_isPreserved() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("private void scheduleListeningRestart(int delayMillis)"));
        assertTrue(service.contains("listeningRestartScheduler.schedule(delayMillis)"));
        assertTrue(service.contains("listeningRestartScheduler.cancel()"));
    }

    @Test
    public void staleRecognitionCallbackProtection_isPreserved() throws Exception {
        String service = serviceSource();

        assertTrue(service.contains("createRecognitionListener(long recognitionGeneration)"));
        assertTrue(countOccurrences(
                service,
                "isCurrentRecognitionGeneration(recognitionGeneration)"
        ) >= 6);
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

    @Test
    public void dataSafety_matchesSystemRecognizerArchitecture() throws Exception {
        String dataSafety = readUtf8(repositoryRoot().resolve("docs/google-play-data-safety.md"));

        assertTrue(dataSafety.contains("The App uses Android's configured recognizer"));
        assertTrue(dataSafety.contains("backend never receives raw microphone audio"));
        assertTrue(dataSafety.contains("Voice or sound recordings | **Yes, conservatively"));
        assertFalse(dataSafety.contains("prefers on-device"));
        assertFalse(dataSafety.contains("compatibility and availability fallback"));
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
