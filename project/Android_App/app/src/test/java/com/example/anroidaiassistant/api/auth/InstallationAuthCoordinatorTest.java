package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

public class InstallationAuthCoordinatorTest {
    @Test
    public void concurrentFirstRequests_registerOnlyOnce() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    return "issued-credential";
                }
        );
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();

        try {
            Callable<String> task = coordinator::getOrRegisterCredential;
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(task));
            }
            for (Future<String> future : futures) {
                assertEquals("issued-credential", future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, registrations.get());
    }

    @Test
    public void concurrentFailedFirstRequests_attemptRegistrationOnlyOnce() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    throw new IOException("registration unavailable");
                }
        );
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> {
                    try {
                        coordinator.getOrRegisterCredential();
                        return false;
                    } catch (IOException expected) {
                        return true;
                    }
                }));
            }
            for (Future<Boolean> future : futures) {
                assertEquals(true, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, registrations.get());
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private volatile String credential;

        @Override
        public String getCredential() {
            return credential;
        }

        @Override
        public void saveCredential(String credential) {
            this.credential = credential;
        }

        @Override
        public void clearCredential() {
            credential = null;
        }
    }
}
