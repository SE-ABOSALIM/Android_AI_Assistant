package com.example.anroidaiassistant.util;

import android.content.Context;
import android.provider.Settings;

public final class DeviceIdentity {
    private DeviceIdentity() {}

    public static String getDeviceId(Context context) {
        if (context == null) {
            return null;
        }

        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        if (androidId == null || androidId.trim().isEmpty()) {
            return null;
        }
        return androidId.trim();
    }
}
