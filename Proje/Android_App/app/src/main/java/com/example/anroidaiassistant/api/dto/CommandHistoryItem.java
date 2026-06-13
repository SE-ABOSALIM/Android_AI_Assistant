package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class CommandHistoryItem {
    private String id;
    private String text;
    private String language;
    private String intent;
    private Map<String, Object> parameters;
    private boolean accepted;

    @SerializedName("result_status")
    private String resultStatus;

    @SerializedName("error_code")
    private String errorCode;

    private Double confidence;

    @SerializedName("processing_time_ms")
    private Double processingTimeMs;

    @SerializedName("created_at")
    private String createdAt;

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }

    public String getIntent() {
        return intent;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Double getProcessingTimeMs() {
        return processingTimeMs;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
