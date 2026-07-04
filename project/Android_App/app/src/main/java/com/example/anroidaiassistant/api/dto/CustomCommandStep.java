package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class CustomCommandStep {
    private String intent;
    private Map<String, Object> parameters = new HashMap<>();

    @SerializedName("wait_after_ms")
    private int waitAfterMs;

    @SerializedName("stop_on_failure")
    private boolean stopOnFailure = true;

    public CustomCommandStep() {
    }

    public CustomCommandStep(String intent, Map<String, Object> parameters) {
        this.intent = intent;
        this.parameters = parameters == null ? new HashMap<>() : parameters;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Map<String, Object> getParameters() {
        return parameters == null ? new HashMap<>() : parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null ? new HashMap<>() : parameters;
    }

    public int getWaitAfterMs() {
        return waitAfterMs;
    }

    public void setWaitAfterMs(int waitAfterMs) {
        this.waitAfterMs = waitAfterMs;
    }

    public boolean isStopOnFailure() {
        return stopOnFailure;
    }

    public void setStopOnFailure(boolean stopOnFailure) {
        this.stopOnFailure = stopOnFailure;
    }
}
