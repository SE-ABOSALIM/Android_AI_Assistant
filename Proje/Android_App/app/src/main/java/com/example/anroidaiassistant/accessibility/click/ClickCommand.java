package com.example.anroidaiassistant.accessibility.click;

import com.example.anroidaiassistant.util.TextNormalizer;

public final class ClickCommand {
    public final String targetText;
    public final int targetIndex;
    public final String position;

    public ClickCommand(String targetText, int targetIndex, String position) {
        this.targetText = targetText == null ? "" : targetText.trim();
        this.targetIndex = targetIndex;
        this.position = position == null ? "" : position.trim();
    }

    public boolean hasTargetText() {
        return TextNormalizer.hasText(targetText);
    }

    public boolean hasTargetIndex() {
        return targetIndex > 0;
    }

    public boolean isValid() {
        return hasTargetText() || hasTargetIndex();
    }
}
