package com.example.anroidaiassistant.permissions;

public interface FeaturePermissionGate {
    boolean ensureGranted(
            AssistantCapability capability,
            Runnable onGranted,
            Runnable onCancelled
    );
}
