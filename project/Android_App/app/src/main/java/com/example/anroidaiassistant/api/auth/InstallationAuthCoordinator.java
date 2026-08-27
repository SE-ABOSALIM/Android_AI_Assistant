package com.example.anroidaiassistant.api.auth;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public final class InstallationAuthCoordinator {
    private final Object registrationLock = new Object();
    private final CredentialStore credentialStore;
    private final InstallationRegistrar registrar;
    private RegistrationAttempt registrationAttempt;

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

        RegistrationAttempt attempt;
        boolean startsAttempt = false;
        synchronized (registrationLock) {
            credential = clean(credentialStore.getCredential());
            if (credential != null) {
                return credential;
            }

            if (registrationAttempt == null) {
                registrationAttempt = new RegistrationAttempt();
                startsAttempt = true;
            }
            attempt = registrationAttempt;
        }

        if (!startsAttempt) {
            return attempt.awaitResult();
        }

        try {
            credential = clean(registrar.registerInstallation());
            if (credential == null) {
                throw new IOException("Installation registration did not issue a credential");
            }
            credentialStore.saveCredential(credential);
            attempt.succeed(credential);
            return credential;
        } catch (IOException exception) {
            attempt.fail(exception);
            throw exception;
        } catch (RuntimeException exception) {
            attempt.fail(new IOException("Installation registration failed unexpectedly", exception));
            throw exception;
        } finally {
            synchronized (registrationLock) {
                if (registrationAttempt == attempt) {
                    registrationAttempt = null;
                }
            }
            attempt.complete();
        }
    }

    public String recoverCredentialAfterUnauthorized(String failedCredential) throws IOException {
        String normalizedFailedCredential = clean(failedCredential);
        synchronized (registrationLock) {
            String currentCredential = clean(credentialStore.getCredential());
            if (currentCredential != null && !currentCredential.equals(normalizedFailedCredential)) {
                return currentCredential;
            }
            if (currentCredential != null) {
                credentialStore.clearCredential();
            }
        }

        return getOrRegisterCredential();
    }

    private static final class RegistrationAttempt {
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile String credential;
        private volatile IOException failure;

        private void succeed(String credential) {
            this.credential = credential;
        }

        private void fail(IOException failure) {
            this.failure = failure;
        }

        private void complete() {
            completed.countDown();
        }

        private String awaitResult() throws IOException {
            try {
                completed.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for installation registration", exception);
            }

            if (failure != null) {
                throw failure;
            }
            if (credential == null) {
                throw new IOException("Installation registration completed without a credential");
            }
            return credential;
        }
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
