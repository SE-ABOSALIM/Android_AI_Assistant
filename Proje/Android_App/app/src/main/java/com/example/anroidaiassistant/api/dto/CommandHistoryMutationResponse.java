package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public class CommandHistoryMutationResponse {
    private boolean accepted;

    @SerializedName("deleted_count")
    private int deletedCount;

    public boolean isAccepted() {
        return accepted;
    }

    public int getDeletedCount() {
        return deletedCount;
    }
}
