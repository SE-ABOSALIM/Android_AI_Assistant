package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;

public class PredictRequest {
    @SerializedName("text")
    private String text;

    @SerializedName("language")
    private String language;

    public PredictRequest(String text, String language) {
        this.text = text;
        this.language = language;
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }
}
