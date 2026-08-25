package com.example.anroidaiassistant.accessibility.consent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class AccessibilityAutomationGate {
    private static final Set<String> ACCESSIBILITY_INTENTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "UNINSTALL_APP",
                    "SCROLL_SCREEN",
                    "SWIPE_GESTURE",
                    "DOUBLE_TAP",
                    "HOLD_SCREEN",
                    "GO_HOME",
                    "GO_BACK",
                    "CLOSE_APP",
                    "SHOW_RECENTS",
                    "OPEN_NOTIFICATIONS",
                    "TAKE_SCREENSHOT",
                    "POWER_OFF",
                    "RESTART_DEVICE",
                    "SHOW_GRID",
                    "SHOW_LABELS",
                    "TAKE_PHOTO",
                    "SEARCH_QUERY",
                    "WRITE_TEXT",
                    "CLEAR_TEXT",
                    "SET_INPUT_FOCUS",
                    "CLICK_ITEM",
                    "SET_WIFI",
                    "SET_BLUETOOTH",
                    "SET_LOCATION",
                    "SET_MOBILE_DATA",
                    "SET_MOBILE_HOTSPOT",
                    "SET_KEYBOARD"
            ))
    );

    private final AccessibilityConsentState consentState;
    private final BooleanSupplier serviceConnected;

    public AccessibilityAutomationGate(
            AccessibilityConsentState consentState,
            BooleanSupplier serviceConnected
    ) {
        this.consentState = consentState;
        this.serviceConnected = serviceConnected;
    }

    public Decision evaluate(String intent) {
        if (!requiresAccessibility(intent)) {
            return Decision.ALLOW;
        }
        if (!consentState.hasCurrentConsent()) {
            return Decision.CONSENT_REQUIRED;
        }
        if (!serviceConnected.getAsBoolean()) {
            return Decision.SERVICE_REQUIRED;
        }
        return Decision.ALLOW;
    }

    public boolean canAccessAccessibilityData() {
        return consentState.hasCurrentConsent() && serviceConnected.getAsBoolean();
    }

    private boolean requiresAccessibility(String intent) {
        return intent != null
                && ACCESSIBILITY_INTENTS.contains(intent.trim().toUpperCase(Locale.US));
    }

    public enum Decision {
        ALLOW,
        CONSENT_REQUIRED,
        SERVICE_REQUIRED
    }
}
