package com.example.anroidaiassistant.api.dto;

import com.example.anroidaiassistant.session.AssistantSession;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class PredictRequest {
    @SerializedName("text")
    private String text;

    @SerializedName("language")
    private String language;

    @SerializedName("text_alternatives")
    private List<String> textAlternatives;

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("catalog_version")
    private String catalogVersion;

    public PredictRequest(String text, String language) {
        this(text, language, AssistantSession.getSessionId());
    }

    public PredictRequest(String text, String language, String sessionId) {
        this(text, language, sessionId, null);
    }

    public PredictRequest(String text, String language, String sessionId, List<String> textAlternatives) {
        this.text = text;
        this.language = language;
        this.textAlternatives = textAlternatives == null ? new ArrayList<>() : new ArrayList<>(textAlternatives);
        this.sessionId = sessionId;
        this.catalogVersion = AssistantSession.getCatalogVersion();
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getTextAlternatives() {
        return textAlternatives;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }
}
