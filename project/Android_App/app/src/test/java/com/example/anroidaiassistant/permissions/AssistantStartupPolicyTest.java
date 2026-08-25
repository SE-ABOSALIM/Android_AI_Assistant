package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class AssistantStartupPolicyTest {
    private final AssistantStartupPolicy policy = new AssistantStartupPolicy();

    @Test
    public void assistantStartup_requiresMicrophone() {
        assertFalse(policy.canActivate(stateWithout(AssistantCapability.MICROPHONE)));
    }

    @Test
    public void assistantStartup_requiresAccessibility() {
        assertFalse(policy.canActivate(stateWithout(AssistantCapability.ACCESSIBILITY_SERVICE)));
    }

    @Test
    public void assistantStartup_requiresPopupCapability() {
        assertFalse(policy.canActivate(stateWithout(AssistantCapability.POPUP)));
    }

    @Test
    public void assistantStartup_doesNotRequireContacts() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.CONTACTS)));
    }

    @Test
    public void assistantStartup_doesNotRequireCallPermission() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.DIRECT_CALL)));
    }

    @Test
    public void assistantStartup_doesNotRequirePhoneState() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.PHONE_STATE)));
    }

    @Test
    public void assistantStartup_doesNotRequireAnswerCall() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.ANSWER_CALL)));
    }

    @Test
    public void assistantStartup_doesNotRequireCamera() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.CAMERA)));
    }

    @Test
    public void assistantStartup_doesNotRequireSoundModeAccess() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.SOUND_MODE)));
    }

    @Test
    public void assistantStartup_doesNotRequireSystemSettingsAccess() {
        assertTrue(policy.canActivate(stateWithout(AssistantCapability.SYSTEM_SETTINGS)));
    }

    @Test
    public void startupInformationUi_listsOnlyCoreRequirements() {
        assertEquals(
                Arrays.asList(
                        AssistantCapability.MICROPHONE,
                        AssistantCapability.ACCESSIBILITY_SERVICE,
                        AssistantCapability.POPUP
                ),
                policy.missingCoreCapabilities(AssistantCapabilityState.noneGranted())
        );
    }

    @Test
    public void optionalPermissions_areNotRequestedAutomaticallyAtStartup() {
        AssistantCapabilityState state = AssistantCapabilityState.noneGranted()
                .withGranted(AssistantCapability.MICROPHONE)
                .withGranted(AssistantCapability.ACCESSIBILITY_SERVICE)
                .withGranted(AssistantCapability.POPUP);

        assertTrue(policy.canActivate(state));
        assertTrue(policy.missingCoreCapabilities(state).isEmpty());
    }

    private AssistantCapabilityState stateWithout(AssistantCapability capability) {
        return AssistantCapabilityState.allGranted().withDenied(capability);
    }
}
