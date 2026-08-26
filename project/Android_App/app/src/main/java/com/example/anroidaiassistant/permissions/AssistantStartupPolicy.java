package com.example.anroidaiassistant.permissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AssistantStartupPolicy {
    private static final List<AssistantCapability> ACTIONABLE_CORE_CAPABILITIES =
            Collections.unmodifiableList(Arrays.asList(
                    AssistantCapability.MICROPHONE,
                    AssistantCapability.ACCESSIBILITY_SERVICE
            ));

    public boolean canActivate(AssistantCapabilityState state) {
        return missingCoreCapabilities(state).isEmpty();
    }

    public List<AssistantCapability> missingCoreCapabilities(AssistantCapabilityState state) {
        List<AssistantCapability> missing = new ArrayList<>();
        for (AssistantCapability capability : ACTIONABLE_CORE_CAPABILITIES) {
            boolean granted = state.isGranted(capability);
            if (capability == AssistantCapability.ACCESSIBILITY_SERVICE) {
                granted = granted && state.isGranted(AssistantCapability.POPUP);
            }
            if (!granted) {
                missing.add(capability);
            }
        }
        return missing;
    }
}
