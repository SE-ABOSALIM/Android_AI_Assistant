package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public class AppCatalogResponse {
    private boolean accepted;

    @SerializedName("app_count")
    private int appCount;

    @SerializedName("catalog_version")
    private String catalogVersion;

    public boolean isAccepted() {
        return accepted;
    }

    public int getAppCount() {
        return appCount;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }
}
