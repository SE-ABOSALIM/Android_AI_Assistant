package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;

public class PredictRequest {
    @SerializedName("text")
    private String text;

    @SerializedName("language")
    private String language;

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("catalog_version")
    private String catalogVersion;

    public PredictRequest(String text, String language) {
        this(text, language, AssistantSession.getSessionId());
    }

    public PredictRequest(String text, String language, String sessionId) {
        this.text = text;
        this.language = language;
        this.sessionId = sessionId;
        this.catalogVersion = AssistantSession.getCatalogVersion();
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }
}
