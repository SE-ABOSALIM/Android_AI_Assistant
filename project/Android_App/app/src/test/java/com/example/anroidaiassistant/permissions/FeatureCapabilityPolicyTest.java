package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;

public class FeatureCapabilityPolicyTest {
    private final FeatureCapabilityPolicy policy = new FeatureCapabilityPolicy();

    @Test
    public void openApp_worksWithoutOptionalSensitivePermissions() {
        assertNull(policy.requiredCapability("OPEN_APP", Collections.emptyMap()));
    }

    @Test
    public void contactFeature_requestsContactsOnlyWhenInvoked() {
        assertEquals(
                AssistantCapability.CONTACTS,
                policy.requiredCapability(
                        "CALL_CONTACT",
                        Collections.singletonMap("contact_name", "Ahmet")
                )
        );
    }

    @Test
    public void callFeature_requestsCallAccessOnlyWhenActuallyRequired() {
        assertNull(policy.requiredCapability(
                "CALL_CONTACT",
                Collections.singletonMap("contact_name", "+90 555 123 45 67")
        ));
    }

    @Test
    public void phoneStateFeature_requestsAccessOnlyWhenInvoked_ifRequired() {
        assertNull(policy.requiredCapability("START_ASSISTANT", Collections.emptyMap()));
    }

    @Test
    public void answerCallFeature_requestsAccessOnlyWhenInvoked_ifRequired() {
        assertEquals(
                AssistantCapability.ANSWER_CALL,
                policy.requiredCapability("ANSWER_CALL", Collections.emptyMap())
        );
        assertEquals(
                AssistantCapability.ANSWER_CALL,
                policy.requiredCapability("REJECT_CALL", Collections.emptyMap())
        );
    }

    @Test
    public void cameraFeature_requestsPermissionOnlyWhenActuallyRequired() {
        assertNull(policy.requiredCapability("TAKE_PHOTO", Collections.emptyMap()));
        assertEquals(
                AssistantCapability.CAMERA,
                policy.requiredCapability(
                        "SET_FLASHLIGHT",
                        Collections.singletonMap("state", "on")
                )
        );
    }

    @Test
    public void soundModeFeature_requestsSpecialAccessOnlyWhenInvoked() {
        assertEquals(
                AssistantCapability.SOUND_MODE,
                policy.requiredCapability(
                        "SET_SOUND_MODE",
                        Collections.singletonMap("sound_mode", "silent")
                )
        );
    }

    @Test
    public void systemSettingsFeature_requestsSpecialAccessOnlyWhenInvoked() {
        assertEquals(
                AssistantCapability.SYSTEM_SETTINGS,
                policy.requiredCapability(
                        "ADJUST_BRIGHTNESS",
                        Collections.singletonMap("brightness", "increase")
                )
        );
    }

    @Test
    public void incompleteFeatureCommand_doesNotRequestAccessBeforeProtectedStep() {
        assertNull(policy.requiredCapability("CALL_CONTACT", Collections.emptyMap()));
        assertNull(policy.requiredCapability("SET_FLASHLIGHT", Collections.emptyMap()));
        assertNull(policy.requiredCapability("SET_SOUND_MODE", Collections.emptyMap()));
        assertNull(policy.requiredCapability("ADJUST_BRIGHTNESS", Collections.emptyMap()));
    }
}
