package com.example.anroidaiassistant.permissions;

import com.example.anroidaiassistant.util.ParameterReader;

import java.util.Locale;
import java.util.Map;

public final class FeatureCapabilityPolicy {
    public AssistantCapability requiredCapability(
            String intent,
            Map<String, Object> parameters
    ) {
        if (intent == null) {
            return null;
        }

        switch (intent.trim().toUpperCase(Locale.US)) {
            case "CALL_CONTACT":
                return requiresContactLookup(parameters)
                        ? AssistantCapability.CONTACTS
                        : null;
            case "ANSWER_CALL":
            case "REJECT_CALL":
                return AssistantCapability.ANSWER_CALL;
            case "SET_FLASHLIGHT":
                return hasSupportedValue(parameters, "state", "on", "off")
                        ? AssistantCapability.CAMERA
                        : null;
            case "SET_SOUND_MODE":
                return hasSupportedValue(
                        parameters,
                        "sound_mode",
                        "normal",
                        "mute",
                        "silent",
                        "vibrate"
                ) ? AssistantCapability.SOUND_MODE : null;
            case "ADJUST_BRIGHTNESS":
                return hasSupportedValue(parameters, "brightness", "increase", "decrease")
                        ? AssistantCapability.SYSTEM_SETTINGS
                        : null;
            default:
                return null;
        }
    }

    private boolean requiresContactLookup(Map<String, Object> parameters) {
        String value = ParameterReader.getStringParam(parameters, "contact_name");
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        if (!value.matches(".*\\d.*")) {
            return true;
        }
        String normalized = value.replaceAll("[^0-9+]", "");
        return normalized.replace("+", "").length() < 3;
    }

    private boolean hasSupportedValue(
            Map<String, Object> parameters,
            String key,
            String... supportedValues
    ) {
        String value = ParameterReader.getStringParam(parameters, key);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        for (String supportedValue : supportedValues) {
            if (supportedValue.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
