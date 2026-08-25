package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.anroidaiassistant.api.dto.InstallationRegistrationRequest;
import com.example.anroidaiassistant.util.DeviceIdentity;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class StableDeviceBootstrapTest {
    @Test
    public void clearDataStyleAndroidBootstrap_usesSameStableIdentity() throws Exception {
        String stableAndroidIdBeforeClear = DeviceIdentity.normalizeStableAndroidId("android-id-x");
        String stableAndroidIdAfterClear = DeviceIdentity.normalizeStableAndroidId(" android-id-x ");
        InMemoryCredentialStore emptyStore = new InMemoryCredentialStore();
        AtomicReference<InstallationRegistrationRequest> sentRequest = new AtomicReference<>();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                emptyStore,
                () -> {
                    sentRequest.set(InstallationRegistrationRequestFactory.create(
                            stableAndroidIdAfterClear,
                            "1.0",
                            "TR"
                    ));
                    return "recovered-credential";
                }
        );

        String credential = coordinator.getOrRegisterCredential();

        assertEquals(stableAndroidIdBeforeClear, stableAndroidIdAfterClear);
        assertEquals("android-id-x", sentRequest.get().getDeviceId());
        assertEquals("recovered-credential", credential);
        assertEquals("recovered-credential", emptyStore.getCredential());
    }

    @Test
    public void blankAndroidId_doesNotGenerateRandomFallbackIdentity() {
        assertNull(DeviceIdentity.normalizeStableAndroidId(null));
        assertNull(DeviceIdentity.normalizeStableAndroidId("   "));
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private String credential;

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
