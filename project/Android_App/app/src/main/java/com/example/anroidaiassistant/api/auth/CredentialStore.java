package com.example.anroidaiassistant.api.auth;

import java.io.IOException;

public interface CredentialStore {
    String getCredential() throws IOException;

    void saveCredential(String credential) throws IOException;

    void clearCredential();
}
