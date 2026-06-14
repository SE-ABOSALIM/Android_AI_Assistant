package com.example.anroidaiassistant.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.UUID;

public final class DeviceIdentity {
    private static final String PREFS_NAME = "assistant_device_identity";
    private static final String FALLBACK_DEVICE_ID_KEY = "fallback_device_id";

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
            return getFallbackDeviceId(context);
        }
        return androidId.trim();
    }

    private static String getFallbackDeviceId(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existing = preferences.getString(FALLBACK_DEVICE_ID_KEY, null);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing.trim();
        }

        String generated = "android-local-" + UUID.randomUUID();
        preferences.edit().putString(FALLBACK_DEVICE_ID_KEY, generated).apply();
        return generated;
    }
}
