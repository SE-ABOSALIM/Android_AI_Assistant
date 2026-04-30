package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AppCatalogRequest {
    @SerializedName("session_id")
    private final String sessionId;

    @SerializedName("catalog_version")
    private final String catalogVersion;

    private final List<AppCatalogEntry> apps;

    public AppCatalogRequest(String sessionId, String catalogVersion, List<AppCatalogEntry> apps) {
        this.sessionId = sessionId;
        this.catalogVersion = catalogVersion;
        this.apps = apps;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public List<AppCatalogEntry> getApps() {
        return apps;
    }
}
