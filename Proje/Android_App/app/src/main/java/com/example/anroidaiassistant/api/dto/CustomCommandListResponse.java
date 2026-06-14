package com.example.anroidaiassistant.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomCommandListResponse {
    private List<CustomCommandItem> items = new ArrayList<>();

    public List<CustomCommandItem> getItems() {
        return items == null ? new ArrayList<>() : items;
    }
}
