package com.example.anroidaiassistant.permissions;

import java.util.EnumSet;

public final class AssistantCapabilityState {
    private final EnumSet<AssistantCapability> grantedCapabilities;

    private AssistantCapabilityState(EnumSet<AssistantCapability> grantedCapabilities) {
        this.grantedCapabilities = grantedCapabilities.clone();
    }

    public static AssistantCapabilityState noneGranted() {
        return new AssistantCapabilityState(EnumSet.noneOf(AssistantCapability.class));
    }

    public static AssistantCapabilityState allGranted() {
        return new AssistantCapabilityState(EnumSet.allOf(AssistantCapability.class));
    }

    public AssistantCapabilityState withGranted(AssistantCapability capability) {
        EnumSet<AssistantCapability> updated = grantedCapabilities.clone();
        updated.add(capability);
        return new AssistantCapabilityState(updated);
    }

    public AssistantCapabilityState withDenied(AssistantCapability capability) {
        EnumSet<AssistantCapability> updated = grantedCapabilities.clone();
        updated.remove(capability);
        return new AssistantCapabilityState(updated);
    }

    public boolean isGranted(AssistantCapability capability) {
        return grantedCapabilities.contains(capability);
    }
}
