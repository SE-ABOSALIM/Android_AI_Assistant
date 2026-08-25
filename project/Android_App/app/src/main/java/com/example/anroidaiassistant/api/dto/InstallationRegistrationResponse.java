package com.example.anroidaiassistant.api.dto;

import com.google.gson.annotations.SerializedName;

public final class InstallationRegistrationResponse {
    private String credential;
    @SerializedName("token_type")
    private String tokenType;

    public String getCredential() {
        return credential;
    }

    public String getTokenType() {
        return tokenType;
    }
}
