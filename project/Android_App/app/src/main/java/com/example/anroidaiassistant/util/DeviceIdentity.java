package com.example.anroidaiassistant.util;

import android.content.Context;
import android.provider.Settings;

public final class DeviceIdentity {
    private DeviceIdentity() {}

    /**
     * Returns the Android-scoped stable ownership identifier sent to the backend.
     * This identifier is not a secret or cryptographic proof of device possession.
     * TODO: Bind recovery to Play Integrity/app attestation before high-trust
     * public-production use.
     */
    public static String getDeviceId(Context context) {
        if (context == null) {
            return null;
        }

        return normalizeStableAndroidId(
                Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ANDROID_ID
                )
        );
    }

    public static String normalizeStableAndroidId(String androidId) {
        if (androidId == null || androidId.trim().isEmpty()) {
            return null;
        }
        return androidId.trim();
    }
}
