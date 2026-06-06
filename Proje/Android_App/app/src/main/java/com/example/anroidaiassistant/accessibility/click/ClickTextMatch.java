package com.example.anroidaiassistant.accessibility.click;

public final class ClickTextMatch {
    public final int score;
    public final String reason;

    public ClickTextMatch(int score, String reason) {
        this.score = score;
        this.reason = reason == null ? "" : reason;
    }
}
