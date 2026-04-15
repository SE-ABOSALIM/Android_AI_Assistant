package com.example.anroidaiassistant;

public class PredictResponse {
    private String input;
    private String predicted_label;
    private double confidence;
    private boolean accepted;
    private double temperature;

    public String getInput() {
        return input;
    }

    public String getPredicted_label() {
        return predicted_label;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public double getTemperature() {
        return temperature;
    }
}