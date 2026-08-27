package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void concurrentCallers_shareSingleFailedRegistrationAttempt() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        CountDownLatch callersStarted = new CountDownLatch(20);
        CountDownLatch registrationStarted = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    registrationStarted.countDown();
                    await(releaseFailure);
                    throw new IOException("registration unavailable");
                }
        );
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> {
                    callersStarted.countDown();
                    try {
                        coordinator.getOrRegisterCredential();
                        return false;
                    } catch (IOException expected) {
                        return true;
                    }
                }));
            }
            callersStarted.await(5, TimeUnit.SECONDS);
            registrationStarted.await(5, TimeUnit.SECONDS);
            releaseFailure.countDown();
            for (Future<Boolean> future : futures) {
                assertEquals(true, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, registrations.get());
    }

    @Test
    public void transientRegistrationFailure_laterRequestRetries() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    if (registrations.incrementAndGet() == 1) {
                        throw new IOException("temporary failure");
                    }
                    return "recovered-credential";
                }
        );

        assertThrows(IOException.class, coordinator::getOrRegisterCredential);

        assertEquals("recovered-credential", coordinator.getOrRegisterCredential());
        assertEquals(2, registrations.get());
    }

    @Test
    public void failedAttempt_doesNotCreateImmediateRetryLoop() {
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                new InMemoryCredentialStore(),
                () -> {
                    registrations.incrementAndGet();
                    throw new IOException("temporary failure");
                }
        );

        assertThrows(IOException.class, coordinator::getOrRegisterCredential);

        assertEquals(1, registrations.get());
    }

    @Test
    public void successfulRetry_persistsAndReturnsCredential() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    if (registrations.incrementAndGet() == 1) {
                        throw new IOException("temporary failure");
                    }
                    return "persisted-credential";
                }
        );

        assertThrows(IOException.class, coordinator::getOrRegisterCredential);
        assertEquals("persisted-credential", coordinator.getOrRegisterCredential());

        assertEquals("persisted-credential", store.getCredential());
        assertEquals("persisted-credential", coordinator.getOrRegisterCredential());
        assertEquals(2, registrations.get());
    }

    @Test
    public void concurrentRetryAfterFailure_stillPerformsSingleRegistration() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        CountDownLatch retryStarted = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    int attempt = registrations.incrementAndGet();
                    if (attempt == 1) {
                        throw new IOException("temporary failure");
                    }
                    retryStarted.countDown();
                    await(releaseRetry);
                    return "retry-credential";
                }
        );
        assertThrows(IOException.class, coordinator::getOrRegisterCredential);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 12; index++) {
                futures.add(executor.submit(coordinator::getOrRegisterCredential));
            }
            retryStarted.await(5, TimeUnit.SECONDS);
            releaseRetry.countDown();
            for (Future<String> future : futures) {
                assertEquals("retry-credential", future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, registrations.get());
    }

    @Test
    public void unexpectedRegistrationFailure_doesNotLeaveAttemptStuck() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                new InMemoryCredentialStore(),
                () -> {
                    if (registrations.incrementAndGet() == 1) {
                        throw new IllegalStateException("unexpected registration failure");
                    }
                    return "recovered-credential";
                }
        );

        assertThrows(IllegalStateException.class, coordinator::getOrRegisterCredential);

        assertEquals("recovered-credential", coordinator.getOrRegisterCredential());
        assertEquals(2, registrations.get());
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for test coordination", exception);
        }
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
