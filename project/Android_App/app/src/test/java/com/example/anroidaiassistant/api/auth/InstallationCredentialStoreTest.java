package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class InstallationCredentialStoreTest {
    @Test
    public void bearerCredential_isEncryptedBeforePreferenceStorage() throws Exception {
        MapStorage storage = new MapStorage();
        SecretKey key = newSecretKey();
        InstallationCredentialStore credentialStore = new InstallationCredentialStore(
                storage,
                () -> key
        );

        credentialStore.saveCredential("raw-bearer-credential");

        assertFalse(storage.values.containsValue("raw-bearer-credential"));
        assertEquals("raw-bearer-credential", credentialStore.getCredential());
    }

    @Test
    public void malformedCredential_isClearedAndReRegistered() throws Exception {
        MapStorage storage = new MapStorage();
        storage.values.put("encrypted_bearer_credential", "not-a-supported-payload");
        SecretKey key = newSecretKey();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                new InstallationCredentialStore(storage, () -> key),
                () -> {
                    registrations.incrementAndGet();
                    return "replacement-credential";
                }
        );

        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals(1, registrations.get());
        assertEquals(1, storage.removeCount);
    }

    @Test
    public void corruptedCiphertext_isClearedAndReRegistered() throws Exception {
        MapStorage storage = new MapStorage();
        SecretKey key = newSecretKey();
        InstallationCredentialStore credentialStore = new InstallationCredentialStore(storage, () -> key);
        credentialStore.saveCredential("old-credential");
        String payload = storage.values.get("encrypted_bearer_credential");
        char replacement = payload.endsWith("0") ? '1' : '0';
        storage.values.put(
                "encrypted_bearer_credential",
                payload.substring(0, payload.length() - 1) + replacement
        );
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                credentialStore,
                () -> {
                    registrations.incrementAndGet();
                    return "replacement-credential";
                }
        );

        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals("replacement-credential", credentialStore.getCredential());
        assertEquals(1, registrations.get());
        assertEquals(1, storage.removeCount);
    }

    @Test
    public void unusableKeystoreKey_recoversWithNewCredential() throws Exception {
        MapStorage storage = new MapStorage();
        SecretKey originalKey = newSecretKey();
        InstallationCredentialStore originalStore = new InstallationCredentialStore(
                storage,
                () -> originalKey
        );
        originalStore.saveCredential("old-credential");
        SecretKey replacementKey = newSecretKey();
        InstallationCredentialStore restoredStore = new InstallationCredentialStore(
                storage,
                () -> replacementKey
        );
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                restoredStore,
                () -> {
                    registrations.incrementAndGet();
                    return "replacement-credential";
                }
        );

        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals("replacement-credential", restoredStore.getCredential());
        assertEquals(1, registrations.get());
        assertEquals(1, storage.removeCount);
    }

    @Test
    public void invalidatedKeystoreKey_isResetBeforeReRegistration() throws Exception {
        MapStorage storage = new MapStorage();
        SecretKey originalKey = newSecretKey();
        new InstallationCredentialStore(storage, () -> originalKey)
                .saveCredential("old-credential");
        ResettableInvalidKeyProvider keyProvider = new ResettableInvalidKeyProvider();
        InstallationCredentialStore credentialStore = new InstallationCredentialStore(
                storage,
                keyProvider
        );
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                credentialStore,
                () -> {
                    registrations.incrementAndGet();
                    return "replacement-credential";
                }
        );

        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals("replacement-credential", credentialStore.getCredential());
        assertEquals(1, registrations.get());
        assertEquals(1, keyProvider.resetCount);
        assertEquals(1, storage.removeCount);
    }

    @Test
    public void transientStoreFailure_doesNotDestructivelyDeleteCredential() throws Exception {
        MapStorage storage = new MapStorage();
        SecretKey key = newSecretKey();
        InstallationCredentialStore healthyStore = new InstallationCredentialStore(storage, () -> key);
        healthyStore.saveCredential("stored-credential");
        String persistedPayload = storage.values.get("encrypted_bearer_credential");
        AtomicInteger registrations = new AtomicInteger();
        InstallationCredentialStore temporarilyUnavailableStore = new InstallationCredentialStore(
                storage,
                () -> {
                    throw new IOException("temporary keystore I/O failure");
                }
        );
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                temporarilyUnavailableStore,
                () -> {
                    registrations.incrementAndGet();
                    return "must-not-register";
                }
        );

        assertThrows(IOException.class, coordinator::getOrRegisterCredential);

        assertEquals(persistedPayload, storage.values.get("encrypted_bearer_credential"));
        assertEquals(0, storage.removeCount);
        assertEquals(0, registrations.get());
    }

    @Test
    public void unreadableCredential_doesNotRemainPersistentlyStuck() throws Exception {
        MapStorage storage = new MapStorage();
        storage.values.put("encrypted_bearer_credential", "v1:invalid-hex:payload");
        SecretKey key = newSecretKey();
        AtomicInteger registrations = new AtomicInteger();
        InstallationCredentialStore credentialStore = new InstallationCredentialStore(
                storage,
                () -> key
        );
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                credentialStore,
                () -> {
                    registrations.incrementAndGet();
                    return "replacement-credential";
                }
        );

        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals("replacement-credential", coordinator.getOrRegisterCredential());
        assertEquals(1, registrations.get());
    }

    private static SecretKey newSecretKey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, new SecureRandom());
        return keyGenerator.generateKey();
    }

    private static final class MapStorage implements InstallationCredentialStore.KeyValueStorage {
        private final Map<String, String> values = new HashMap<>();
        private int removeCount;

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            removeCount++;
            values.remove(key);
        }
    }

    private static final class ResettableInvalidKeyProvider
            implements InstallationCredentialStore.SecretKeyProvider {
        private final SecretKey invalidKey = new SecretKeySpec(new byte[1], "AES");
        private final SecretKey replacementKey;
        private boolean reset;
        private int resetCount;

        private ResettableInvalidKeyProvider() throws GeneralSecurityException {
            replacementKey = newSecretKey();
        }

        @Override
        public SecretKey getOrCreateKey() {
            return reset ? replacementKey : invalidKey;
        }

        @Override
        public void resetKey() {
            resetCount++;
            reset = true;
        }
    }
}
