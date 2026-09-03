package com.example.anroidaiassistant.accessibility.click;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

public final class ClickCandidate {
    public final AccessibilityNodeInfo clickNode;
    public final Rect bounds;
    public final Rect actionBounds;
    public final String label;
    public final int score;
    public final String reason;
    public final String matchSource;
    public final String matchedTarget;
    public final String matchedText;
    public final boolean preferBoundsTap;
    final ClickMatchClass matchClass;
    final String matchFamily;

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
        this(
                clickNode,
                bounds,
                bounds,
                label,
                score,
                ClickMatchClass.fromReason(reason),
                reason,
                "",
                "",
                "",
                "",
                preferBoundsTap
        );
    }

    public ClickCandidate(
            AccessibilityNodeInfo clickNode,
            Rect bounds,
            Rect actionBounds,
            String label,
            int score,
            ClickMatchClass matchClass,
            String reason,
            String matchSource,
            String matchedTarget,
            String matchedText,
            String matchFamily,
            boolean preferBoundsTap
    ) {
        this.clickNode = clickNode;
        this.bounds = new Rect(bounds);
        this.actionBounds = actionBounds == null ? new Rect(bounds) : new Rect(actionBounds);
        this.label = label == null ? "" : label;
        this.score = score;
        this.matchClass = matchClass == null ? ClickMatchClass.NONE : matchClass;
        this.reason = reason == null ? "" : reason;
        this.matchSource = matchSource == null ? "" : matchSource;
        this.matchedTarget = matchedTarget == null ? "" : matchedTarget;
        this.matchedText = matchedText == null ? "" : matchedText;
        this.matchFamily = matchFamily == null ? "" : matchFamily;
        this.preferBoundsTap = preferBoundsTap;
    }
}
