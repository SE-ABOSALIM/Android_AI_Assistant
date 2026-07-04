package com.example.anroidaiassistant.apps;

public final class AppMatch {
    private final String label;
    private final String packageName;
    private final float score;
    private final boolean exact;

    public AppMatch(String label, String packageName, float score, boolean exact) {
        this.label = label;
        this.packageName = packageName;
        this.score = score;
        this.exact = exact;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public float getScore() {
        return score;
    }

    public boolean isExact() {
        return exact;
    }
}