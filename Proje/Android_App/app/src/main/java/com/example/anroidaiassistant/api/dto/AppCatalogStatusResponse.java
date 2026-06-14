package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public class AppCatalogStatusResponse {
    private boolean accepted;

    @SerializedName("session_id")
    private String sessionId;

    private boolean available;

    @SerializedName("catalog_version")
    private String catalogVersion;

    @SerializedName("app_count")
    private int appCount;

    public boolean isAccepted() {
        return accepted;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public int getAppCount() {
        return appCount;
    }
}
