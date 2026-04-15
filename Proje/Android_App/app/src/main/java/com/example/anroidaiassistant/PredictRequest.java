package com.example.anroidaiassistant;

public class PredictRequest {
    private String text;
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