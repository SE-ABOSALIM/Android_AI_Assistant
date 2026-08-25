package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public final class InstallationRegistrationRequest {
    @SerializedName("device_id")
    private final String deviceId;
    private final String platform;
    @SerializedName("app_version")
    private final String appVersion;
    private final String language;

    public InstallationRegistrationRequest(
            String deviceId,
            String platform,
            String appVersion,
            String language
    ) {
        this.deviceId = deviceId;
        this.platform = platform;
        this.appVersion = appVersion;
        this.language = language;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
