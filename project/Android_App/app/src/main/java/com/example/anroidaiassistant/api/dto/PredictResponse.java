package com.example.anroidaiassistant.api.dto;

import com.example.anroidaiassistant.util.ParameterReader;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class PredictResponse {
    private String intent;
    private Map<String, Object> parameters;
    private boolean accepted;

    @SerializedName("missing_slots")
    private List<String> missingSlots;

    @SerializedName("error_code")
    private String errorCode;

    @SerializedName("error_message")
    private String errorMessage;

    public String getIntent() {
        return intent;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<String> getMissingSlots() {
        return missingSlots;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getParameterAsString(String key) {
        return getStringParam(parameters, key);
    }

    public int getParameterAsInt(String key, int defaultValue) {
        int value = getIntParam(parameters, key);
        return value == -1 ? defaultValue : value;
    }

    public static String getStringParam(Map<String, Object> params, String key) {
        return ParameterReader.getStringParam(params, key);
    }

    public static int getIntParam(Map<String, Object> params, String key) {
        return ParameterReader.getIntParam(params, key);
    }

}
