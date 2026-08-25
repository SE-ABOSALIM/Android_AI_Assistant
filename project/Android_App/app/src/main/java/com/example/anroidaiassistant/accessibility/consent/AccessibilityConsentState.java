package com.example.anroidaiassistant.accessibility.consent;

public interface AccessibilityConsentState {
    boolean hasCurrentConsent();

    void recordCurrentConsent();
}
