package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AppCatalogRequest {
    @SerializedName("session_id")
    private final String sessionId;

    private final String language;

    @SerializedName("catalog_version")
    private final String catalogVersion;

    private final List<AppCatalogEntry> apps;

    public AppCatalogRequest(String sessionId, String language, String catalogVersion, List<AppCatalogEntry> apps) {
        this.sessionId = sessionId;
        this.language = language;
        this.catalogVersion = catalogVersion;
        this.apps = apps;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLanguage() {
        return language;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public List<AppCatalogEntry> getApps() {
        return apps;
    }
}
