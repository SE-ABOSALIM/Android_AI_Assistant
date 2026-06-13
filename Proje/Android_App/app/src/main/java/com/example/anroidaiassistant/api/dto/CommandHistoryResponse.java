package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class CommandHistoryResponse {
    private List<CommandHistoryItem> items = new ArrayList<>();

    @SerializedName("total_count")
    private int totalCount;

    @SerializedName("successful_count")
    private int successfulCount;

    @SerializedName("failed_count")
    private int failedCount;

    private int limit;
    private int offset;

    @SerializedName("has_more")
    private boolean hasMore;

    public List<CommandHistoryItem> getItems() {
        return items == null ? new ArrayList<>() : items;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessfulCount() {
        return successfulCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
