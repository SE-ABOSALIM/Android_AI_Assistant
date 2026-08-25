package com.example.anroidaiassistant.api.auth;

import com.example.anroidaiassistant.api.dto.InstallationRegistrationRequest;
import com.example.anroidaiassistant.util.DeviceIdentity;

import java.io.IOException;

public final class InstallationRegistrationRequestFactory {
    private InstallationRegistrationRequestFactory() {}

    public static InstallationRegistrationRequest create(
            String stableAndroidId,
            String appVersion,
            String language
    ) throws IOException {
        String deviceId = DeviceIdentity.normalizeStableAndroidId(stableAndroidId);
        if (deviceId == null) {
            throw new IOException("Stable Android identity is unavailable");
        }
        return new InstallationRegistrationRequest(
                deviceId,
                "android",
                appVersion,
                language
        );
    }
}
