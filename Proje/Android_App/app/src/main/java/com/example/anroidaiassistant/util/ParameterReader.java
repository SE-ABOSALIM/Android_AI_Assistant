package com.example.anroidaiassistant.util;

import java.util.Map;

public final class ParameterReader {
    private ParameterReader() {}

    public static String getStringParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return null;
        }
        return String.valueOf(params.get(key));
    }

    public static int getIntParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return -1;
        }

        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            String textValue = String.valueOf(value);
            if (textValue.contains(".")) {
                return (int) Double.parseDouble(textValue);
            }
            return Integer.parseInt(textValue);
        } catch (Exception ignored) {
            return -1;
        }
    }
}