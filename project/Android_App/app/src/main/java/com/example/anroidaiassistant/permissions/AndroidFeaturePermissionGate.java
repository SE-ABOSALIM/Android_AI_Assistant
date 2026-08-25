package com.example.anroidaiassistant.permissions;

import android.content.Context;

public final class AndroidFeaturePermissionGate implements FeaturePermissionGate {
    private final Context context;

    public AndroidFeaturePermissionGate(Context context) {
        this.context = context;
    }

    @Override
    public boolean ensureGranted(
            AssistantCapability capability,
            Runnable onGranted,
            Runnable onCancelled
    ) {
        if (FeaturePermissionAccess.isGranted(context, capability)) {
            return true;
        }
        FeaturePermissionFlow.requestFromFeature(
                context,
                capability,
                onGranted,
                onCancelled
        );
        return false;
    }
}
