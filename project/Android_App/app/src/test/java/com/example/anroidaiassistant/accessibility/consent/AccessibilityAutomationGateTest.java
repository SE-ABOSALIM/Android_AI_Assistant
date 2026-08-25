package com.example.anroidaiassistant.accessibility.consent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccessibilityAutomationGateTest {
    @Test
    public void accessibilityEnabledWithoutDisclosure_doesNotBypassConsentGate() {
        AccessibilityAutomationGate gate = gate(false, true);

        assertEquals(
                AccessibilityAutomationGate.Decision.CONSENT_REQUIRED,
                gate.evaluate("CLICK_ITEM")
        );
    }

    @Test
    public void accessibilityExecutionAllowedWhenConsentAndServiceStateAreValid() {
        AccessibilityAutomationGate gate = gate(true, true);

        assertEquals(
                AccessibilityAutomationGate.Decision.ALLOW,
                gate.evaluate("CLICK_ITEM")
        );
    }

    @Test
    public void accessibilityExecutionBlockedWhenServiceIsUnavailable() {
        AccessibilityAutomationGate gate = gate(true, false);

        assertEquals(
                AccessibilityAutomationGate.Decision.SERVICE_REQUIRED,
                gate.evaluate("CLICK_ITEM")
        );
    }

    @Test
    public void nonAccessibilityFlow_remainsUsableWithoutConsentOrService() {
        AccessibilityAutomationGate gate = gate(false, false);

        assertEquals(
                AccessibilityAutomationGate.Decision.ALLOW,
                gate.evaluate("OPEN_APP")
        );
    }

    private AccessibilityAutomationGate gate(boolean consent, boolean serviceConnected) {
        return new AccessibilityAutomationGate(
                new AccessibilityConsentState() {
                    @Override
                    public boolean hasCurrentConsent() {
                        return consent;
                    }

                    @Override
                    public void recordCurrentConsent() {}
                },
                () -> serviceConnected
        );
    }
}
