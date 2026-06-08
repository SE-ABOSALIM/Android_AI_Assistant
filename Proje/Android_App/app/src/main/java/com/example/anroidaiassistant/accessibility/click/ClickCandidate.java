package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

public final class ClickCandidate {
    public final AccessibilityNodeInfo clickNode;
    public final Rect bounds;
    public final String label;
    public final int score;
    public final String reason;
    public final boolean preferBoundsTap;

    public ClickCandidate(
            AccessibilityNodeInfo clickNode,
            Rect bounds,
            String label,
            int score,
            String reason
    ) {
        this(clickNode, bounds, label, score, reason, false);
    }

    public ClickCandidate(
            AccessibilityNodeInfo clickNode,
            Rect bounds,
            String label,
            int score,
            String reason,
            boolean preferBoundsTap
    ) {
        this.clickNode = clickNode;
        this.bounds = new Rect(bounds);
        this.label = label == null ? "" : label;
        this.score = score;
        this.reason = reason == null ? "" : reason;
        this.preferBoundsTap = preferBoundsTap;
    }
}
