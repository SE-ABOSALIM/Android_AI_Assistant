package com.example.anroidaiassistant.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

public class AccessibilityServiceConfigurationTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";

    @Test
    public void accessibilityService_doesNotSubscribeToAllEvents() throws Exception {
        Element config = serviceConfig();

        assertFalse(config.hasAttributeNS(ANDROID_NAMESPACE, "accessibilityEventTypes"));
        assertFalse(serviceConfigSource().contains("typeAllMask"));
    }

    @Test
    public void noProductionConsumerRequiresAllEventTypes() throws Exception {
        String production = allProductionJava();
        String service = productionSource("MyAccessibilityService.java");

        assertFalse(production.contains("AccessibilityEvent.TYPES_ALL_MASK"));
        assertFalse(production.contains("getEventType("));
        assertTrue(service.matches(
                "(?s).*onAccessibilityEvent\\(AccessibilityEvent event\\)\\s*\\{\\s*}.*"
        ));
    }

    @Test
    public void runtimeServiceInfo_doesNotRestoreEventSubscription() throws Exception {
        String production = allProductionJava();

        assertFalse(production.contains("setServiceInfo("));
        assertFalse(production.contains("serviceInfo.eventTypes"));
        assertFalse(production.contains("TYPES_ALL_MASK"));
    }

    @Test
    public void rootWindowAccess_remainsAvailableUnderMinimalEventConfiguration() throws Exception {
        Element config = serviceConfig();
        String production = allProductionJava();

        assertFalse(config.hasAttributeNS(ANDROID_NAMESPACE, "accessibilityEventTypes"));
        assertEquals("true", config.getAttributeNS(
                ANDROID_NAMESPACE,
                "canRetrieveWindowContent"
        ));
        assertTrue(config.getAttributeNS(ANDROID_NAMESPACE, "accessibilityFlags")
                .contains("flagRetrieveInteractiveWindows"));
        assertTrue(production.contains("getRootInActiveWindow()"));
    }

    @Test
    public void clickAutomation_doesNotDependOnAccessibilityEventDelivery() throws Exception {
        String source = productionSource("accessibility/click/ClickItemController.java");

        assertFalse(source.contains("AccessibilityEvent"));
        assertTrue(source.contains("getRootInActiveWindow()"));
        assertTrue(source.contains("performAction(AccessibilityNodeInfo.ACTION_CLICK)"));
    }

    @Test
    public void textAutomation_doesNotDependOnAccessibilityEventDelivery() throws Exception {
        String source = productionSource("accessibility/SearchInputController.java");

        assertFalse(source.contains("AccessibilityEvent"));
        assertTrue(source.contains("getRootInActiveWindow()"));
        assertTrue(source.contains("AccessibilityNodeInfo.ACTION_SET_TEXT"));
    }

    @Test
    public void quickSettingsAutomation_doesNotDependOnAccessibilityEventDelivery()
            throws Exception {
        String source = productionSource("accessibility/QuickSettingsTileController.java");

        assertFalse(source.contains("AccessibilityEvent"));
        assertTrue(source.contains("GLOBAL_ACTION_QUICK_SETTINGS"));
        assertTrue(source.contains("getRootInActiveWindow()"));
    }

    @Test
    public void overlaySelection_doesNotDependOnAccessibilityEventDelivery() throws Exception {
        String service = productionSource("MyAccessibilityService.java");
        String selection = productionSource("ui/overlay/SelectionOverlayController.java");
        String grid = productionSource("ui/overlay/GridOverlayController.java");

        assertTrue(service.contains("startNumberSelection("));
        assertFalse(selection.contains("AccessibilityEvent"));
        assertFalse(grid.contains("AccessibilityEvent"));
        assertTrue(selection.contains("TYPE_ACCESSIBILITY_OVERLAY"));
        assertTrue(grid.contains("TYPE_ACCESSIBILITY_OVERLAY"));
    }

    @Test
    public void keyEventFiltering_remainsConfiguredIndependentlyFromAccessibilityEvents()
            throws Exception {
        Element config = serviceConfig();
        String service = productionSource("MyAccessibilityService.java");

        assertTrue(config.getAttributeNS(ANDROID_NAMESPACE, "accessibilityFlags")
                .contains("flagRequestFilterKeyEvents"));
        assertTrue(service.contains("protected boolean onKeyEvent(KeyEvent event)"));
        assertTrue(service.contains("KeyEvent.KEYCODE_BACK"));
    }

    @Test
    public void gestureAndNodeCapabilities_remainConfigured() throws Exception {
        Element config = serviceConfig();

        assertEquals("true", config.getAttributeNS(
                ANDROID_NAMESPACE,
                "canRetrieveWindowContent"
        ));
        assertEquals("true", config.getAttributeNS(ANDROID_NAMESPACE, "canPerformGestures"));
        String flags = config.getAttributeNS(ANDROID_NAMESPACE, "accessibilityFlags");
        assertTrue(flags.contains("flagIncludeNotImportantViews"));
        assertTrue(flags.contains("flagRetrieveInteractiveWindows"));
        assertTrue(flags.contains("flagReportViewIds"));
        assertTrue(flags.contains("flagRequestFilterKeyEvents"));
    }

    private Element serviceConfig() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(serviceConfigPath().toFile()).getDocumentElement();
    }

    private String serviceConfigSource() throws IOException {
        return readUtf8(serviceConfigPath());
    }

    private Path serviceConfigPath() {
        return appRoot().resolve("src/main/res/xml/accessibility_service_config.xml");
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
