package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessibilityPopupCapabilityTest {
    @Test
    public void popupCapability_requiresActualAccessibilityOverlayProvider() {
        assertFalse(AccessibilityPopupCapability.isAvailable(false, true));
    }

    @Test
    public void accessibilityReady_providesPopupCapability() {
        assertTrue(AccessibilityPopupCapability.isAvailable(true, true));
    }

    @Test
    public void accessibilityDisclosureGate_remainsRequiredForPopupCapability() {
        assertFalse(AccessibilityPopupCapability.isAvailable(true, false));
    }
}
