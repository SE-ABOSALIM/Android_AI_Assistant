package com.example.anroidaiassistant.permissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AssistantStartupPolicy {
    private static final List<AssistantCapability> CORE_CAPABILITIES =
            Collections.unmodifiableList(Arrays.asList(
                    AssistantCapability.MICROPHONE,
                    AssistantCapability.ACCESSIBILITY_SERVICE,
                    AssistantCapability.POPUP
            ));

    public boolean canActivate(AssistantCapabilityState state) {
        return missingCoreCapabilities(state).isEmpty();
    }

    public List<AssistantCapability> missingCoreCapabilities(AssistantCapabilityState state) {
        List<AssistantCapability> missing = new ArrayList<>();
        for (AssistantCapability capability : CORE_CAPABILITIES) {
            if (!state.isGranted(capability)) {
                missing.add(capability);
            }
        }
        return missing;
    }
}
