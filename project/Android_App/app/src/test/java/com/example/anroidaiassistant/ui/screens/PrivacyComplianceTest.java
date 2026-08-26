package com.example.anroidaiassistant.ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PrivacyComplianceTest {
    @Test
    public void privacyPolicyExistsAndBundledCopyMatchesRepositoryDocument() throws Exception {
        Path repositoryPolicy = repositoryRoot().resolve("docs/privacy-policy.md");
        Path bundledPolicy = appRoot().resolve("src/main/res/raw/privacy_policy.md");

        assertTrue(Files.isRegularFile(repositoryPolicy));
        assertTrue(Files.isRegularFile(bundledPolicy));
        assertEquals(normalized(repositoryPolicy), normalized(bundledPolicy));
    }

    @Test
    public void dataSafetyDraftExistsAndBlocksUnprovedTransportClaim() throws Exception {
        Path draft = repositoryRoot().resolve("docs/google-play-data-safety.md");

        assertTrue(Files.isRegularFile(draft));
        String source = readUtf8(draft);
        assertTrue(source.contains("BLOCKED UNTIL PRODUCTION HTTPS"));
        assertTrue(source.contains("Voice or sound recordings | **Yes, conservatively"));
        assertTrue(source.contains("Installed apps | **Yes**"));
        assertTrue(source.contains("Device or other IDs | **Yes**"));
        assertTrue(source.contains("rate_limit:registration:<client_host>"));
        assertTrue(source.contains("rate_limit:predict:<device_ref_id>"));
        assertTrue(source.contains("raw text is not persisted in command history"));
        assertTrue(source.contains("raw prediction parameters are not persisted"));
        assertFalse(source.contains("`command_history.text`"));
        assertFalse(source.contains("`command_history.session_id`"));
    }

    @Test
    public void speechFallbackDocumentationExplainsCompatibilityWithoutFixedProviderBlocker()
            throws Exception {
        String policy = readUtf8(repositoryRoot().resolve("docs/privacy-policy.md"));
        String dataSafety = readUtf8(
                repositoryRoot().resolve("docs/google-play-data-safety.md")
        );
        String combined = policy + "\n" + dataSafety;

        assertTrue(policy.contains("compatibility and availability fallback"));
        assertTrue(policy.contains("unavailable or unsupported"));
        assertTrue(policy.contains("may process microphone audio remotely"));
        assertTrue(policy.contains("does not send raw"));
        assertTrue(policy.contains("recognized text"));
        assertTrue(policy.contains("Raw command text is not persisted in command history"));
        assertTrue(policy.contains("Raw prediction parameters are not persisted in command history"));
        assertTrue(dataSafety.contains("Voice or sound recordings | **Yes, conservatively"));
        assertTrue(dataSafety.contains("does not assume one universal provider"));
        assertFalse(dataSafety.contains("production speech-recognition provider retention"));
        assertFalse(dataSafety.contains("provider handling must be verified"));
        assertFalse(combined.contains("continuous recognition"));
        assertFalse(combined.contains("sessionless recognition"));
        assertFalse(combined.contains("permanent listening"));
    }

    @Test
    public void privacyPolicyIsReachableFromSettingsWithoutFakeUrl() throws Exception {
        String layout = readUtf8(appRoot().resolve("src/main/res/layout/fragment_settings.xml"));
        String settings = productionSource("ui/screens/SettingsFragment.java");

        assertTrue(layout.contains("@+id/settingsPrivacyPolicyCard"));
        assertTrue(settings.contains("settingsPrivacyPolicyCard"));
        assertTrue(settings.contains("showPrivacyPolicy()"));
        assertTrue(settings.contains("R.raw.privacy_policy"));
        assertFalse(settings.contains("http://"));
        assertFalse(settings.contains("https://"));
    }

    @Test
    public void bundledPolicyRendersAsReadablePlainText() throws Exception {
        String markdown = readUtf8(appRoot().resolve("src/main/res/raw/privacy_policy.md"));
        String readable = SettingsFragment.readableMarkdown(markdown);

        assertTrue(readable.startsWith("Privacy Policy for Android AI Assistant"));
        assertFalse(readable.contains("# Privacy Policy"));
        assertFalse(readable.contains("**Effective date:**"));
        assertTrue(readable.contains("Effective date:"));
    }

    @Test
    public void androidReleaseLogs_doNotExposeRecognizedSpeechOrAccessibilityLabels()
            throws Exception {
        String service = productionSource("MyAccessibilityService.java");
        String clickController = productionSource(
                "accessibility/click/ClickItemController.java"
        );
        String sensitiveLog = productionSource("util/SensitiveDebugLog.java");
        Pattern speechLog = Pattern.compile(
                "Log\\.[diwev]\\([^;]*(spokenText|latestPartialRecognitionText|matches)",
                Pattern.DOTALL
        );

        assertFalse(speechLog.matcher(service).find());
        assertFalse(clickController.contains("import android.util.Log"));
        assertFalse(Pattern.compile("(?m)^\\s*Log\\.").matcher(clickController).find());
        assertTrue(clickController.contains("SensitiveDebugLog.info"));
        assertTrue(sensitiveLog.contains("if (BuildConfig.DEBUG)"));
    }

    @Test
    public void androidLogs_doNotReferenceAuthSecretsOrStableDeviceId() throws Exception {
        try (Stream<Path> paths = Files.walk(appRoot().resolve("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::assertLogStatementsArePrivacySafe);
        }
    }

    @Test
    public void accessibilityDisclosureAndPrivacyPolicy_doNotContradictEachOther()
            throws Exception {
        String policy = readUtf8(repositoryRoot().resolve("docs/privacy-policy.md"));
        String strings = readUtf8(appRoot().resolve("src/main/res/values/strings.xml"));
        String declaration = readUtf8(
                repositoryRoot().resolve("docs/google-play-accessibility-declaration.md")
        );

        assertTrue(policy.contains("Visible screen text, content descriptions, view identifiers"));
        assertTrue(policy.contains("are not included in backend requests"));
        assertTrue(policy.contains("Content-bearing Accessibility matching logs are disabled in release builds"));
        assertTrue(strings.contains("Content-bearing matching diagnostics are disabled in release builds"));
        assertTrue(declaration.contains("diagnostics are disabled in release builds"));
    }

    private void assertLogStatementsArePrivacySafe(Path sourcePath) {
        try {
            String source = readUtf8(sourcePath);
            for (String line : source.split("\\R")) {
                if (!line.contains("Log.")) {
                    continue;
                }
                assertFalse(sourcePath + " logs Authorization", line.contains("Authorization"));
                assertFalse(sourcePath + " logs bearer value", line.contains("Bearer"));
                assertFalse(sourcePath + " logs ANDROID_ID", line.contains("ANDROID_ID"));
                assertFalse(sourcePath + " logs credential", line.contains("credential"));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + sourcePath, exception);
        }
    }

    private String productionSource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/" + relativePath
        ));
    }

    private String normalized(Path path) throws IOException {
        return readUtf8(path).replace("\r\n", "\n").trim();
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
