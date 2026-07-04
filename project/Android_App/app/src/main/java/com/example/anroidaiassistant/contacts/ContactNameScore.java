package com.example.anroidaiassistant.contacts;

final class ContactNameScore {
    private final int score;
    private final boolean exact;

    ContactNameScore(int score, boolean exact) {
        this.score = score;
        this.exact = exact;
    }

    int getScore() {
        return score;
    }

    boolean isExact() {
        return exact;
    }
}