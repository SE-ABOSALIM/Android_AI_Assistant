package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.util.TextNormalizer;

public final class ClickCommand {
    public final String targetText;
    public final String position;

    public ClickCommand(String targetText, String position) {
        this.targetText = targetText == null ? "" : targetText.trim();
        this.position = position == null ? "" : position.trim();
    }

    public boolean hasTargetText() {
        return TextNormalizer.hasText(targetText);
    }

    public boolean isValid() {
        return hasTargetText();
    }
}
