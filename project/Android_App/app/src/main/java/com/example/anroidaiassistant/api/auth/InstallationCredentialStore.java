package com.example.anroidaiassistant.api.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class InstallationCredentialStore implements CredentialStore {
    private static final String PREFERENCES_NAME = "assistant_installation_auth";
    private static final String ENCRYPTED_CREDENTIAL_KEY = "encrypted_bearer_credential";
    private static final String KEY_ALIAS = "assistant_installation_bearer_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PAYLOAD_VERSION = "v1";

    interface KeyValueStorage {
        String get(String key);

        void put(String key, String value) throws IOException;

        void remove(String key);
    }

    interface SecretKeyProvider {
        SecretKey getOrCreateKey() throws GeneralSecurityException, IOException;

        default void resetKey() throws GeneralSecurityException, IOException {}
    }

    private final KeyValueStorage storage;
    private final SecretKeyProvider keyProvider;

    public InstallationCredentialStore(Context context) {
        Context appContext = context.getApplicationContext();
        this.storage = new SharedPreferencesStorage(
                appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        );
        this.keyProvider = new AndroidKeystoreKeyProvider();
    }

    InstallationCredentialStore(
            KeyValueStorage storage,
            SecretKeyProvider keyProvider
    ) {
        this.storage = storage;
        this.keyProvider = keyProvider;
    }

    @Override
    public synchronized String getCredential() throws IOException {
        String payload = storage.get(ENCRYPTED_CREDENTIAL_KEY);
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }

        String[] parts = payload.split(":", 3);
        if (parts.length != 3 || !PAYLOAD_VERSION.equals(parts[0])) {
            return discardUnusableCredential(false);
        }

        byte[] iv;
        byte[] ciphertext;
        try {
            iv = fromHex(parts[1]);
            ciphertext = fromHex(parts[2]);
        } catch (IllegalArgumentException exception) {
            return discardUnusableCredential(false);
        }

        SecretKey key;
        try {
            key = keyProvider.getOrCreateKey();
        } catch (GeneralSecurityException exception) {
            throw new IOException("Installation credential key is unavailable", exception);
        }

        Cipher cipher;
        try {
            cipher = Cipher.getInstance(TRANSFORMATION);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException exception) {
            throw new IOException("Installation credential encryption is unavailable", exception);
        }

        try {
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(128, iv)
            );
        } catch (InvalidKeyException exception) {
            return discardUnusableCredential(true);
        } catch (InvalidAlgorithmParameterException | IllegalArgumentException exception) {
            return discardUnusableCredential(false);
        }

        try {
            return new String(
                    cipher.doFinal(ciphertext),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (BadPaddingException | IllegalBlockSizeException exception) {
            return discardUnusableCredential(false);
        }
    }

    @Override
    public synchronized void saveCredential(String credential) throws IOException {
        if (credential == null || credential.trim().isEmpty()) {
            throw new IOException("Installation credential is empty");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(
                    credential.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            String payload = PAYLOAD_VERSION
                    + ":" + toHex(cipher.getIV())
                    + ":" + toHex(ciphertext);
            storage.put(ENCRYPTED_CREDENTIAL_KEY, payload);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Installation credential could not be encrypted", exception);
        }
    }

    @Override
    public synchronized void clearCredential() {
        storage.remove(ENCRYPTED_CREDENTIAL_KEY);
    }

    private String discardUnusableCredential(boolean resetKey) throws IOException {
        storage.remove(ENCRYPTED_CREDENTIAL_KEY);
        if (resetKey) {
            try {
                keyProvider.resetKey();
            } catch (GeneralSecurityException exception) {
                throw new IOException("Unusable installation credential key could not be reset", exception);
            }
        }
        return null;
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(item & 0x0f, 16));
        }
        return result.toString();
    }

    private static byte[] fromHex(String value) {
        if (value == null || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid hex payload");
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex payload");
            }
            result[index / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static final class SharedPreferencesStorage implements KeyValueStorage {
        private final SharedPreferences preferences;

        private SharedPreferencesStorage(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String get(String key) {
            return preferences.getString(key, null);
        }

        @Override
        public void put(String key, String value) throws IOException {
            if (!preferences.edit().putString(key, value).commit()) {
                throw new IOException("Encrypted credential could not be persisted");
            }
        }

        @Override
        public void remove(String key) {
            preferences.edit().remove(key).apply();
        }
    }

    private static final class AndroidKeystoreKeyProvider implements SecretKeyProvider {
        @Override
        public SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
            KeyStore keyStore = loadKeyStore();
            java.security.Key existingKey = keyStore.getKey(KEY_ALIAS, null);
            if (existingKey instanceof SecretKey) {
                return (SecretKey) existingKey;
            }
            if (existingKey != null) {
                keyStore.deleteEntry(KEY_ALIAS);
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
            );
            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                    )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
            );
            return keyGenerator.generateKey();
        }

        @Override
        public void resetKey() throws GeneralSecurityException, IOException {
            KeyStore keyStore = loadKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        }

        private static KeyStore loadKeyStore() throws GeneralSecurityException, IOException {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return keyStore;
        }
    }
}
