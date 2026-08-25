package com.example.anroidaiassistant.permissions;

public final class AccessibilityPopupCapability {
    private AccessibilityPopupCapability() {}

    public static boolean isAvailable(
            boolean accessibilityOverlayProviderConnected,
            boolean currentDisclosureConsent
    ) {
        return accessibilityOverlayProviderConnected && currentDisclosureConsent;
    }
}
