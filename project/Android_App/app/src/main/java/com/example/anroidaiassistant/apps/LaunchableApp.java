package com.example.anroidaiassistant.apps;

public final class LaunchableApp {
    private final String label;
    private final String packageName;

    public LaunchableApp(String label, String packageName) {
        this.label = label;
        this.packageName = packageName;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }
}
