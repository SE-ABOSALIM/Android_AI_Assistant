package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PermissionsUiSemanticsTest {
    @Test
    public void permissionsUi_showsOnlyTwoCoreSetupItems() throws Exception {
        String corePermissions = corePermissionsSource();

        assertEquals(2, countOccurrences(corePermissions, "items.add("));
        assertTrue(corePermissions.contains("permission_microphone_title"));
        assertTrue(corePermissions.contains("permission_accessibility_title"));
    }

    @Test
    public void permissionsUi_doesNotShowAssistantPopupsAsSeparatePermission() throws Exception {
        String corePermissions = corePermissionsSource();

        assertFalse(corePermissions.contains("permission_popup_title"));
        assertFalse(corePermissions.contains("permission_popup_description"));
        assertFalse(corePermissions.contains("ic_perm_popup"));
        assertFalse(corePermissions.contains("AssistantCapability.POPUP"));
    }

    @Test
    public void corePrerequisiteCount_isTwo() throws Exception {
        String layout = readUtf8(appRoot().resolve("src/main/res/layout/fragment_permissions.xml"));

        assertTrue(layout.contains("android:text=\"0/2\""));
        assertFalse(layout.contains("android:text=\"0/3\""));
    }

    @Test
    public void accessibilityServiceDescription_mentionsPopupCapability() throws Exception {
        assertLocaleDescriptions(
                "values/strings.xml",
                "assistant popups",
                "selection overlays"
        );
        assertLocaleDescriptions(
                "values-tr/strings.xml",
                "asistan açılır pencereleri",
                "seçim katmanları"
        );
        assertLocaleDescriptions(
                "values-ar/strings.xml",
                "نوافذ المساعد المنبثقة",
                "طبقات التحديد"
        );
    }

    @Test
    public void noPopupRowNavigationExists() throws Exception {
        String corePermissions = corePermissionsSource();

        assertEquals(1, countOccurrences(corePermissions, "this::requestAccessibilitySetup"));
        assertFalse(corePermissions.contains("FeaturePermissionAccess"));
    }

    @Test
    public void accessibilityConsentFlow_remainsIntact() throws Exception {
        String fragment = productionSource("ui/screens/PermissionsFragment.java");

        assertTrue(fragment.contains("AccessibilityDisclosureFlow.show("));
        assertTrue(fragment.contains("this::requestAccessibilitySetup"));
    }

    private void assertLocaleDescriptions(
            String relativePath,
            String popupPhrase,
            String selectionPhrase
    ) throws IOException {
        String strings = readUtf8(appRoot().resolve("src/main/res/" + relativePath));
        String permissionDescription = stringResource(
                strings,
                "permission_accessibility_description"
        );
        String serviceDescription = stringResource(
                strings,
                "accessibility_service_description"
        );

        assertTrue(relativePath, permissionDescription.contains(popupPhrase));
        assertTrue(relativePath, permissionDescription.contains(selectionPhrase));
        assertTrue(relativePath, serviceDescription.contains(popupPhrase));
        assertTrue(relativePath, serviceDescription.contains(selectionPhrase));
    }

    private String corePermissionsSource() throws IOException {
        String source = productionSource("ui/screens/PermissionsFragment.java");
        int start = source.indexOf("private List<PermissionItem> corePermissions()");
        int end = source.indexOf("private List<PermissionItem> optionalPermissions()", start);
        if (start < 0 || end < 0) {
            throw new AssertionError("Could not locate core permissions model");
        }
        return source.substring(start, end);
    }

    private String stringResource(String source, String resourceName) {
        String marker = "<string name=\"" + resourceName + "\">";
        int start = source.indexOf(marker);
        int end = source.indexOf("</string>", start);
        if (start < 0 || end < 0) {
            throw new AssertionError("Missing string resource: " + resourceName);
        }
        return source.substring(start + marker.length(), end);
    }

    private int countOccurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private String productionSource(String relativePath) throws IOException {
        return readUtf8(appRoot().resolve(
                "src/main/java/com/example/anroidaiassistant/" + relativePath
        ));
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
