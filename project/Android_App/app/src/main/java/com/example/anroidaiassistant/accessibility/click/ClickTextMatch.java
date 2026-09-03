package com.example.anroidaiassistant.accessibility.click;

public final class ClickTextMatch {
    public final int score;
    public final String reason;
    public final String matchedTarget;
    final ClickMatchClass matchClass;

    public ClickTextMatch(int score, String reason) {
        this(score, reason, "");
    }

    public ClickTextMatch(int score, String reason, String matchedTarget) {
        this(score, reason, matchedTarget, ClickMatchClass.fromReason(reason));
    }

    ClickTextMatch(int score, String reason, String matchedTarget, ClickMatchClass matchClass) {
        this.score = score;
        this.reason = reason == null ? "" : reason;
        this.matchedTarget = matchedTarget == null ? "" : matchedTarget;
        this.matchClass = matchClass == null ? ClickMatchClass.NONE : matchClass;
    }
}
