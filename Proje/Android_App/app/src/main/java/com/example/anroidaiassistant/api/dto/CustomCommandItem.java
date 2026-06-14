package com.example.anroidaiassistant.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomCommandItem {
    private String id;
    private String name;
    private String language;
    private boolean enabled = true;
    private List<CustomCommandStep> steps = new ArrayList<>();
    private String created_at;
    private String updated_at;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<CustomCommandStep> getSteps() {
        return steps == null ? new ArrayList<>() : steps;
    }

    public String getCreatedAt() {
        return created_at;
    }

    public String getUpdatedAt() {
        return updated_at;
    }
}
