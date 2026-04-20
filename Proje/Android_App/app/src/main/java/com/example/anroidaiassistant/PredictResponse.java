package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class PredictResponse {
    private String input;
    
    @SerializedName("intent")
    private String intent;

    private boolean accepted;
    private double temperature;
    @SerializedName(value = "parameters", alternate = {"params", "Params"})
    private Map<String, Object> parameters;
    
    @SerializedName("missing_slots")
    private List<String> missingSlots;

    public String getInput() {
        return input;
    }

    public String getIntent() {
        return intent;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public double getTemperature() {
        return temperature;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public List<String> getMissingSlots() {
        return missingSlots;
    }

    // Helper to get string parameter safely
    public String getParameterAsString(String key) {
        if (parameters == null || !parameters.containsKey(key)) return null;
        Object val = parameters.get(key);
        return val != null ? val.toString() : null;
    }

    // Helper to get int parameter safely
    public int getParameterAsInt(String key, int defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) return defaultValue;
        Object val = parameters.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            // Handle case where GSON might parse number as double string e.g. "600.0"
            String strVal = val.toString();
            if (strVal.contains(".")) {
                return (int) Double.parseDouble(strVal);
            }
            return Integer.parseInt(strVal);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
