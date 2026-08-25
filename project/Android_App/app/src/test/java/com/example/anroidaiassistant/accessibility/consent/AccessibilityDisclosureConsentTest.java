package com.example.anroidaiassistant.accessibility.consent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessibilityDisclosureConsentTest {
    @Test
    public void acceptDisclosure_recordsCurrentVersion() {
        InMemoryVersionStore store = new InMemoryVersionStore(0);
        AccessibilityDisclosureConsent consent = new AccessibilityDisclosureConsent(store);

        consent.recordCurrentConsent();

        assertTrue(consent.hasCurrentConsent());
        assertTrue(store.acceptedVersion == AccessibilityDisclosureConsent.CURRENT_VERSION);
    }

    @Test
    public void oldDisclosureVersion_requiresConsentAgain() {
        AccessibilityDisclosureConsent consent = new AccessibilityDisclosureConsent(
                new InMemoryVersionStore(AccessibilityDisclosureConsent.CURRENT_VERSION - 1)
        );

        assertFalse(consent.hasCurrentConsent());
    }

    private static final class InMemoryVersionStore
            implements AccessibilityDisclosureConsent.VersionStore {
        private int acceptedVersion;

        private InMemoryVersionStore(int acceptedVersion) {
            this.acceptedVersion = acceptedVersion;
        }

        @Override
        public int readAcceptedVersion() {
            return acceptedVersion;
        }

        @Override
        public void writeAcceptedVersion(int version) {
            acceptedVersion = version;
        }
    }
}
