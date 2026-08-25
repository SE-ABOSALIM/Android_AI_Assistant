package com.example.anroidaiassistant.accessibility.consent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AccessibilitySetupCoordinator {
    private final AccessibilityConsentState consentState;

    public AccessibilitySetupCoordinator(AccessibilityConsentState consentState) {
        this.consentState = consentState;
    }

    public void requestSetup(boolean serviceEnabled, Host host) {
        if (consentState.hasCurrentConsent()) {
            host.openAccessibilitySettings();
            return;
        }

        AtomicBoolean handled = new AtomicBoolean(false);
        host.showDisclosure(new Decision() {
            @Override
            public void accept() {
                if (!handled.compareAndSet(false, true)) {
                    return;
                }
                consentState.recordCurrentConsent();
                if (!serviceEnabled) {
                    host.openAccessibilitySettings();
                }
            }

            @Override
            public void decline() {
                handled.compareAndSet(false, true);
            }

            @Override
            public void dismiss() {
                handled.compareAndSet(false, true);
            }
        });
    }

    public interface Host {
        void showDisclosure(Decision decision);

        void openAccessibilitySettings();
    }

    public interface Decision {
        void accept();

        void decline();

        void dismiss();
    }
}
