package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class PopupOverlayArchitectureTest {
    private static final String SYSTEM_ALERT_WINDOW =
            "android.permission.SYSTEM_ALERT_WINDOW";
    private static final List<String> OVERLAY_CONTROLLERS = Arrays.asList(
            "ListeningOverlayController.java",
            "ClickTargetOverlayController.java",
            "GridOverlayController.java",
            "SelectionOverlayController.java",
            "UninstallConfirmationOverlayController.java"
    );

    @Test
    public void sourceManifest_doesNotContainSystemAlertWindow() throws Exception {
        assertFalse(sourceManifest().contains(SYSTEM_ALERT_WINDOW));
    }

    @Test
    public void mergedManifest_doesNotContainSystemAlertWindow() throws Exception {
        assertFalse(mergedDebugManifest().contains(SYSTEM_ALERT_WINDOW));
    }

    @Test
    public void popupCapability_doesNotDependOnCanDrawOverlays_ifAccessibilityOverlayIsUsed()
            throws Exception {
        assertFalse(allProductionJava().contains("Settings.canDrawOverlays"));
    }

    @Test
    public void permissionsUi_doesNotDirectUserToOverlaySettings_whenPermissionIsUnnecessary()
            throws Exception {
        String source = productionSource("ui/screens/PermissionsFragment.java");

        assertFalse(source.contains("ACTION_MANAGE_OVERLAY_PERMISSION"));
        assertFalse(source.contains("canDrawOverlays"));
    }

    @Test
    public void allProductionOverlays_useAccessibilityOverlayWindowType() throws Exception {
        for (String controller : OVERLAY_CONTROLLERS) {
            String source = productionSource("ui/overlay/" + controller);

            assertTrue(
                    controller + " must use TYPE_ACCESSIBILITY_OVERLAY",
                    source.contains("WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY")
            );
        }
    }

    @Test
    public void noProductionOverlayUsesSystemAlertWindowDependentType() throws Exception {
        String source = allProductionJava();

        assertFalse(source.contains("TYPE_APPLICATION_OVERLAY"));
        assertFalse(source.contains("TYPE_PHONE"));
        assertFalse(source.contains("TYPE_SYSTEM_ALERT"));
        assertFalse(source.contains("TYPE_SYSTEM_OVERLAY"));
    }

    @Test
    public void accessibilityService_ownsEveryProductionOverlayController() throws Exception {
        String service = productionSource("MyAccessibilityService.java");

        for (String controller : OVERLAY_CONTROLLERS) {
            String simpleName = controller.substring(0, controller.length() - ".java".length());
            assertTrue(
                    simpleName + " must be constructed by MyAccessibilityService",
                    service.contains("new " + simpleName + "(this, windowManager")
            );
        }
    }

    @Test
    public void ambiguousAppSelection_canUseAccessibilityOverlay() throws Exception {
        String presenter = productionSource("apps/AppChoicePresenter.java");
        String selectionOverlay = productionSource("ui/overlay/SelectionOverlayController.java");

        assertTrue(presenter.contains("MyAccessibilityService.getInstance()"));
        assertTrue(presenter.contains("service.startNumberSelection("));
        assertTrue(selectionOverlay.contains("TYPE_ACCESSIBILITY_OVERLAY"));
    }

    @Test
    public void gridAndElementSelection_useAccessibilityOverlay() throws Exception {
        assertTrue(productionSource("ui/overlay/GridOverlayController.java")
                .contains("TYPE_ACCESSIBILITY_OVERLAY"));
        assertTrue(productionSource("ui/overlay/ClickTargetOverlayController.java")
                .contains("TYPE_ACCESSIBILITY_OVERLAY"));
    }

    private String sourceManifest() throws IOException {
        return readUtf8(appRoot().resolve("src/main/AndroidManifest.xml"));
    }

    private String mergedDebugManifest() throws IOException {
        Path manifest = appRoot().resolve(
                "build/intermediates/merged_manifest/debug/"
                        + "processDebugMainManifest/AndroidManifest.xml"
        );
        assertTrue("Merged debug manifest is missing: " + manifest, Files.isRegularFile(manifest));
        return readUtf8(manifest);
    }

    private String productionSource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/" + relativePath
        ));
    }

    private String allProductionJava() throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(appRoot().resolve("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> appendSource(source, path));
        }
        return source.toString();
    }

    private void appendSource(StringBuilder destination, Path source) {
        try {
            destination.append(readUtf8(source)).append('\n');
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + source, exception);
        }
    }

    private String readUtf8(Path source) throws IOException {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private Path appRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
    }
}
