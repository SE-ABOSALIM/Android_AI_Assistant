package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public class CustomCommandMutationResponse {
    private boolean accepted;
    private CustomCommandItem item;

    @SerializedName("deleted_count")
    private int deletedCount;

    @SerializedName("error_code")
    private String errorCode;

    @SerializedName("error_message")
    private String errorMessage;

    public boolean isAccepted() {
        return accepted;
    }

    public CustomCommandItem getItem() {
        return item;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
