package com.example.anroidaiassistant.ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogUiPolishTest {
    @Test
    public void sharedDialogTheme_usesSemanticColorsInDayAndNightModes() throws Exception {
        String styles = resource("values/dialog_styles.xml");
        String dayTheme = resource("values/themes.xml");
        String nightTheme = resource("values-night/themes.xml");

        assertTrue(dayTheme.contains("ThemeOverlay.AnroidAIAssistant.MaterialAlertDialog"));
        assertTrue(nightTheme.contains("ThemeOverlay.AnroidAIAssistant.MaterialAlertDialog"));
        assertTrue(styles.contains("@color/app_surface"));
        assertTrue(styles.contains("@color/app_text_primary"));
        assertTrue(styles.contains("@color/app_text_secondary"));
        assertTrue(styles.contains("@color/app_primary_dark"));
        assertTrue(styles.contains("@color/app_card_border"));
        assertFalse(styles.matches("(?s).*#[0-9a-fA-F]{6,8}.*"));

        assertDialogTextContrast(resource("values/colors.xml"), "light");
        assertDialogTextContrast(resource("values-night/colors.xml"), "dark");
    }

    @Test
    public void privacyDialog_usesStructuredScrollableThemeAwareContent() throws Exception {
        String layout = resource("layout/dialog_privacy_policy.xml");
        String settings = productionSource("ui/screens/SettingsFragment.java");

        assertTrue(layout.contains("<ScrollView"));
        assertTrue(layout.contains("TextAppearance.AnroidAIAssistant.PrivacyPolicy.Body"));
        assertTrue(layout.contains("@color/app_primary_dark"));
        assertFalse(layout.matches("(?s).*#[0-9a-fA-F]{6,8}.*"));
        assertTrue(settings.contains("PrivacyPolicyFormatter.format"));
        assertTrue(settings.contains("LinkMovementMethod.getInstance()"));
        assertTrue(settings.contains("R.layout.dialog_privacy_policy"));
        assertFalse(settings.contains(".setView(content,"));
    }

    @Test
    public void simpleAppDialogs_useSharedMaterialBuilder() throws Exception {
        String mainActivity = productionSource("MainActivity.java");
        String permissions = productionSource("permissions/FeaturePermissionFlow.java");
        String accessibility = productionSource(
                "accessibility/consent/AccessibilityDisclosureFlow.java"
        );

        assertFalse(mainActivity.contains("new AlertDialog.Builder(this)"));
        assertTrue(mainActivity.contains("new MaterialAlertDialogBuilder(this)"));
        assertTrue(permissions.contains("new MaterialAlertDialogBuilder(activity)"));
        assertTrue(accessibility.contains("new MaterialAlertDialogBuilder(activity)"));
    }

    private String resource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve("src/main/res/" + relativePath));
    }

    private String productionSource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/" + relativePath
        ));
    }

    private String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void assertDialogTextContrast(String colors, String mode) {
        int surface = colorValue(colors, "app_surface");
        assertContrastAtLeast(mode + " primary text", surface, colorValue(colors, "app_text_primary"));
        assertContrastAtLeast(mode + " secondary text", surface, colorValue(colors, "app_text_secondary"));
        assertContrastAtLeast(mode + " action/link text", surface, colorValue(colors, "app_primary_dark"));
    }

    private int colorValue(String source, String name) {
        Matcher matcher = Pattern.compile(
                "<color\\s+name=\\\"" + Pattern.quote(name) + "\\\">#(?:[0-9a-fA-F]{2})?([0-9a-fA-F]{6})</color>"
        ).matcher(source);
        assertTrue("Missing color resource " + name, matcher.find());
        return Integer.parseInt(matcher.group(1), 16);
    }

    private void assertContrastAtLeast(String label, int first, int second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        double ratio = (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
        assertTrue(label + " contrast was " + ratio, ratio >= 4.5);
    }

    private double luminance(int color) {
        double red = linearized((color >> 16) & 0xff);
        double green = linearized((color >> 8) & 0xff);
        double blue = linearized(color & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private double linearized(int component) {
        double value = component / 255.0;
        return value <= 0.04045
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private Path appRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
    }
}
