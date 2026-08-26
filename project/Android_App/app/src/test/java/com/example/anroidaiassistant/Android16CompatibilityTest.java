package com.example.anroidaiassistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Android16CompatibilityTest {
    @Test
    public void buildTargetsApi36_withoutChangingMinimumSdk() throws Exception {
        String buildConfiguration = readUtf8(appRoot().resolve("build.gradle"));

        assertTrue(buildConfiguration.matches("(?s).*compileSdk\\s+36\\b.*"));
        assertTrue(buildConfiguration.matches("(?s).*targetSdk\\s+36\\b.*"));
        assertTrue(buildConfiguration.matches("(?s).*minSdk\\s+24\\b.*"));
    }

    @Test
    public void mainActivity_handlesEnforcedEdgeToEdgeInsets() throws Exception {
        String activity = productionSource("MainActivity.java");

        assertTrue(activity.contains("EdgeToEdge.enable(this)"));
        assertTrue(activity.contains("R.id.main_content_container"));
        assertTrue(activity.contains("ViewCompat.setOnApplyWindowInsetsListener"));
        assertTrue(activity.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(activity.contains("WindowInsetsCompat.Type.displayCutout()"));
    }

    @Test
    public void activityBackNavigation_usesPredictiveBackCompatibleAndroidxApi() throws Exception {
        String activity = productionSource("MainActivity.java");
        String manifest = readUtf8(appRoot().resolve("src/main/AndroidManifest.xml"));

        assertTrue(activity.contains("getOnBackPressedDispatcher().addCallback"));
        assertTrue(activity.contains("new OnBackPressedCallback"));
        assertFalse(activity.contains("void onBackPressed()"));
        assertFalse(manifest.contains("enableOnBackInvokedCallback=\"false\""));
    }

    private String productionSource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/" + relativePath
        ));
    }

    private String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private Path appRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
    }
}
