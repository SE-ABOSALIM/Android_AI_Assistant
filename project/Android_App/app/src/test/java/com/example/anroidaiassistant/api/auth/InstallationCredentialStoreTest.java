package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class InstallationCredentialStoreTest {
    @Test
    public void bearerCredential_isEncryptedBeforePreferenceStorage() throws Exception {
        MapStorage storage = new MapStorage();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, new SecureRandom());
        SecretKey key = keyGenerator.generateKey();
        InstallationCredentialStore credentialStore = new InstallationCredentialStore(
                storage,
                () -> key
        );

        credentialStore.saveCredential("raw-bearer-credential");

        assertFalse(storage.values.containsValue("raw-bearer-credential"));
        assertEquals("raw-bearer-credential", credentialStore.getCredential());
    }

    private static final class MapStorage implements InstallationCredentialStore.KeyValueStorage {
        private final Map<String, String> values = new HashMap<>();

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
            values.remove(key);
        }
    }
}
