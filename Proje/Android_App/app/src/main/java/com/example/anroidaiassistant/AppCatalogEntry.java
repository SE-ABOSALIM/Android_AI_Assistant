package com.example.anroidaiassistant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AppCatalogEntry {
    private final String label;

    @SerializedName("package_name")
    private final String packageName;

    private final List<String> aliases;

    public AppCatalogEntry(String label, String packageName, List<String> aliases) {
        this.label = label;
        this.packageName = packageName;
        this.aliases = aliases;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
