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

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("catalog_version")
    private String catalogVersion;

    @SerializedName("has_search_input")
    private boolean hasSearchInput;

    public PredictRequest(String text, String language) {
        this(text, language, null, null, false);
    }

    public PredictRequest(
            String text,
            String language,
            String deviceId,
            List<String> textAlternatives,
            boolean hasSearchInput
    ) {
        this.text = text;
        this.language = language;
        this.textAlternatives = textAlternatives == null ? new ArrayList<>() : new ArrayList<>(textAlternatives);
        this.deviceId = deviceId;
        this.catalogVersion = AssistantSession.getCatalogVersion();
        this.hasSearchInput = hasSearchInput;
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

    public String getDeviceId() {
        return deviceId;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public boolean hasSearchInput() {
        return hasSearchInput;
    }
}
