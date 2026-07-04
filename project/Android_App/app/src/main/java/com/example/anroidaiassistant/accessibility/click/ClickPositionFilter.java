package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;

import com.example.anroidaiassistant.util.TextNormalizer;

public final class ClickPositionFilter {
    public String normalizePosition(String position) {
        String normalized = ClickTextUtils.normalize(position);
        switch (normalized) {
            case "top":
            case "bottom":
            case "left":
            case "right":
            case "center":
                return normalized;
            default:
                return "";
        }
    }

    public boolean matches(Rect bounds, String position, int screenWidth, int screenHeight) {
        if (!TextNormalizer.hasText(position)) {
            return true;
        }

        float centerX = bounds.centerX() / (float) screenWidth;
        float centerY = bounds.centerY() / (float) screenHeight;

        switch (position) {
            case "top":
                return centerY <= 0.45f;
            case "bottom":
                return centerY >= 0.55f;
            case "left":
                return centerX <= 0.45f;
            case "right":
                return centerX >= 0.55f;
            case "center":
                return centerX >= 0.25f && centerX <= 0.75f && centerY >= 0.25f && centerY <= 0.75f;
            default:
                return true;
        }
    }

    public int score(Rect bounds, String position, int screenWidth, int screenHeight) {
        if (!TextNormalizer.hasText(position)) {
            return 0;
        }

        float centerX = bounds.centerX() / (float) screenWidth;
        float centerY = bounds.centerY() / (float) screenHeight;

        switch (position) {
            case "top":
                return Math.round((1.0f - centerY) * 12);
            case "bottom":
                return Math.round(centerY * 12);
            case "left":
                return Math.round((1.0f - centerX) * 12);
            case "right":
                return Math.round(centerX * 12);
            case "center":
                return Math.round((1.0f - Math.abs(0.5f - centerX) - Math.abs(0.5f - centerY)) * 8);
            default:
                return 0;
        }
    }
}
