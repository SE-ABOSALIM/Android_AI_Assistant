package com.example.anroidaiassistant.api.auth;

import java.io.IOException;

public final class InstallationAuthCoordinator {
    private final Object registrationLock = new Object();
    private final CredentialStore credentialStore;
    private final InstallationRegistrar registrar;
    private IOException registrationFailure;

    public InstallationAuthCoordinator(
            CredentialStore credentialStore,
            InstallationRegistrar registrar
    ) {
        this.credentialStore = credentialStore;
        this.registrar = registrar;
    }

    public String getOrRegisterCredential() throws IOException {
        String credential = clean(credentialStore.getCredential());
        if (credential != null) {
            return credential;
        }

        synchronized (registrationLock) {
            credential = clean(credentialStore.getCredential());
            if (credential != null) {
                return credential;
            }
            if (registrationFailure != null) {
                throw new IOException("Installation authentication is unavailable", registrationFailure);
            }

            try {
                credential = clean(registrar.registerInstallation());
                if (credential == null) {
                    throw new IOException("Installation registration did not issue a credential");
                }
                credentialStore.saveCredential(credential);
                return credential;
            } catch (IOException exception) {
                registrationFailure = exception;
                throw exception;
            }
        }
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
