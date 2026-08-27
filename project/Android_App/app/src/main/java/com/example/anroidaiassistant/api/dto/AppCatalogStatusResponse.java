package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public class AppCatalogStatusResponse {
    private boolean accepted;

    private boolean available;

    @SerializedName("catalog_version")
    private String catalogVersion;

    private String language;

    @SerializedName("app_count")
    private int appCount;

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public String getLanguage() {
        return language;
    }

    public int getAppCount() {
        return appCount;
    }
}
