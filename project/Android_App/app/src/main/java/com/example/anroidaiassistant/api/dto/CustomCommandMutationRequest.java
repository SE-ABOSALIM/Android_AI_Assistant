package com.example.anroidaiassistant.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomCommandMutationRequest {
    private String device_id;
    private String language;
    private String name;
    private List<CustomCommandStep> steps = new ArrayList<>();

    public CustomCommandMutationRequest(
            String deviceId,
            String language,
            String name,
            List<CustomCommandStep> steps
    ) {
        this.device_id = deviceId;
        this.language = language;
        this.name = name;
        this.steps = steps == null ? new ArrayList<>() : steps;
    }
}
