package com.example.anroidaiassistant.contacts;

public final class ContactPhoneMatch {
    private final String displayName;
    private final String phoneNumber;
    private final int score;
    private final boolean exact;

    public ContactPhoneMatch(String displayName, String phoneNumber, int score, boolean exact) {
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.score = score;
        this.exact = exact;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public int getScore() {
        return score;
    }

    public boolean isExact() {
        return exact;
    }
}