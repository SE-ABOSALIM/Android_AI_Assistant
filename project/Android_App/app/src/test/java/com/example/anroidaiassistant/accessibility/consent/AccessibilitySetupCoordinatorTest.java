package com.example.anroidaiassistant.accessibility.consent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AccessibilitySetupCoordinatorTest {
    @Test
    public void firstAccessibilitySetup_showsDisclosureBeforeSettings() {
        FakeConsent consent = new FakeConsent(false);
        FakeHost host = new FakeHost();

        new AccessibilitySetupCoordinator(consent).requestSetup(false, host);

        assertEquals(List.of("disclosure"), host.events);
        assertNotNull(host.decision);
    }

    @Test
    public void acceptDisclosure_thenRecordsConsentAndAllowsSettingsNavigation() {
        FakeConsent consent = new FakeConsent(false);
        FakeHost host = new FakeHost();
        new AccessibilitySetupCoordinator(consent).requestSetup(false, host);

        host.decision.accept();

        assertTrue(consent.current);
        assertEquals(List.of("disclosure", "settings"), host.events);
    }

    @Test
    public void declineDisclosure_doesNotRecordConsentOrOpenSettings() {
        FakeConsent consent = new FakeConsent(false);
        FakeHost host = new FakeHost();
        new AccessibilitySetupCoordinator(consent).requestSetup(false, host);

        host.decision.decline();

        assertFalse(consent.current);
        assertEquals(List.of("disclosure"), host.events);
    }

    @Test
    public void dismissOrBack_doesNotCountAsConsent() {
        FakeConsent consent = new FakeConsent(false);
        FakeHost host = new FakeHost();
        new AccessibilitySetupCoordinator(consent).requestSetup(false, host);

        host.decision.dismiss();

        assertFalse(consent.current);
        assertEquals(List.of("disclosure"), host.events);
    }

    @Test
    public void existingConsent_doesNotPromptEveryTime() {
        FakeHost host = new FakeHost();

        new AccessibilitySetupCoordinator(new FakeConsent(true)).requestSetup(false, host);

        assertEquals(List.of("settings"), host.events);
    }

    @Test
    public void serviceEnabledWithoutConsent_stillRequiresDisclosure() {
        FakeConsent consent = new FakeConsent(false);
        FakeHost host = new FakeHost();
        new AccessibilitySetupCoordinator(consent).requestSetup(true, host);

        assertEquals(List.of("disclosure"), host.events);

        host.decision.accept();

        assertTrue(consent.current);
        assertEquals(List.of("disclosure"), host.events);
    }

    private static final class FakeConsent implements AccessibilityConsentState {
        private boolean current;

        private FakeConsent(boolean current) {
            this.current = current;
        }

        @Override
        public boolean hasCurrentConsent() {
            return current;
        }

        @Override
        public void recordCurrentConsent() {
            current = true;
        }
    }

    private static final class FakeHost implements AccessibilitySetupCoordinator.Host {
        private final List<String> events = new ArrayList<>();
        private AccessibilitySetupCoordinator.Decision decision;

        @Override
        public void showDisclosure(AccessibilitySetupCoordinator.Decision decision) {
            events.add("disclosure");
            this.decision = decision;
        }

        @Override
        public void openAccessibilitySettings() {
            events.add("settings");
        }
    }
}
