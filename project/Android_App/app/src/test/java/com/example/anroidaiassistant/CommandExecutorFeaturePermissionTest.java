package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;

import com.example.anroidaiassistant.accessibility.consent.AccessibilityAutomationGate;
import com.example.anroidaiassistant.accessibility.consent.AccessibilityConsentState;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.executor.CommandDispatcher;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.permissions.AssistantCapability;
import com.example.anroidaiassistant.permissions.FeatureCapabilityPolicy;
import com.example.anroidaiassistant.permissions.FeaturePermissionGate;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandExecutorFeaturePermissionTest {
    private final Gson gson = new Gson();

    @Test
    public void missingFeaturePermission_blocksProtectedApiBeforeCall() {
        AtomicInteger protectedCalls = new AtomicInteger();
        RecordingGate gate = new RecordingGate(false);
        CommandExecutor executor = executor("SET_FLASHLIGHT", protectedCalls, gate);

        executor.executeCommand("flash-1", response("SET_FLASHLIGHT", "state", "on"));

        assertEquals(0, protectedCalls.get());
        assertEquals(Collections.singletonList(AssistantCapability.CAMERA), gate.requests);
    }

    @Test
    public void grantedFeaturePermission_allowsProtectedAction() {
        AtomicInteger protectedCalls = new AtomicInteger();
        RecordingGate gate = new RecordingGate(true);
        CommandExecutor executor = executor("SET_FLASHLIGHT", protectedCalls, gate);

        executor.executeCommand("flash-1", response("SET_FLASHLIGHT", "state", "on"));

        assertEquals(1, protectedCalls.get());
    }

    @Test
    public void permissionGrantDoesNotDuplicatePendingCommandExecution() {
        AtomicInteger protectedCalls = new AtomicInteger();
        RecordingGate gate = new RecordingGate(false);
        CommandExecutor executor = executor("ANSWER_CALL", protectedCalls, gate);
        PredictResponse response = response("ANSWER_CALL");

        executor.executeCommand("call-1", response);
        executor.executeCommand("call-1", response);
        assertEquals(1, gate.requests.size());

        gate.grant();

        assertEquals(1, protectedCalls.get());
    }

    @Test
    public void deniedOptionalPermission_doesNotDisableAssistant() {
        AtomicInteger protectedCalls = new AtomicInteger();
        RecordingGate gate = new RecordingGate(false);
        CommandExecutor protectedExecutor = executor("SET_FLASHLIGHT", protectedCalls, gate);

        protectedExecutor.executeCommand(
                "flash-1",
                response("SET_FLASHLIGHT", "state", "on")
        );
        gate.deny();

        AtomicInteger openAppCalls = new AtomicInteger();
        CommandExecutor openAppExecutor = executor("OPEN_APP", openAppCalls, gate);
        openAppExecutor.executeCommand("open-1", response("OPEN_APP"));

        assertEquals(0, protectedCalls.get());
        assertEquals(1, openAppCalls.get());
    }

    @Test
    public void deniedPermission_doesNotPromptAgainForSameExecutionId() {
        AtomicInteger protectedCalls = new AtomicInteger();
        RecordingGate gate = new RecordingGate(false);
        CommandExecutor executor = executor("SET_FLASHLIGHT", protectedCalls, gate);
        PredictResponse response = response("SET_FLASHLIGHT", "state", "on");

        executor.executeCommand("flash-1", response);
        gate.deny();
        executor.executeCommand("flash-1", response);

        assertEquals(1, gate.requests.size());
        assertEquals(0, protectedCalls.get());
    }

    private CommandExecutor executor(
            String intent,
            AtomicInteger calls,
            FeaturePermissionGate gate
    ) {
        CommandHandler handler = new CommandHandler() {
            @Override
            public String getIntent() {
                return intent;
            }

            @Override
            public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
                calls.incrementAndGet();
            }
        };
        AccessibilityConsentState consent = new AccessibilityConsentState() {
            @Override
            public boolean hasCurrentConsent() {
                return true;
            }

            @Override
            public void recordCurrentConsent() {}
        };
        return new CommandExecutor(
                new CommandDispatcher(Collections.singletonList(handler)),
                new CommandExecutionContext(null, ignored -> {}),
                new AccessibilityAutomationGate(consent, () -> true),
                () -> {},
                new FeatureCapabilityPolicy(),
                gate
        );
    }

    private PredictResponse response(String intent) {
        return gson.fromJson(
                "{\"accepted\":true,\"intent\":\"" + intent + "\",\"parameters\":{}}",
                PredictResponse.class
        );
    }

    private PredictResponse response(
            String intent,
            String parameterName,
            String parameterValue
    ) {
        return gson.fromJson(
                "{\"accepted\":true,\"intent\":\"" + intent
                        + "\",\"parameters\":{\"" + parameterName
                        + "\":\"" + parameterValue + "\"}}",
                PredictResponse.class
        );
    }

    private static final class RecordingGate implements FeaturePermissionGate {
        private final List<AssistantCapability> requests = new ArrayList<>();
        private boolean granted;
        private Runnable pendingGrant;
        private Runnable pendingCancel;

        RecordingGate(boolean granted) {
            this.granted = granted;
        }

        @Override
        public boolean ensureGranted(
                AssistantCapability capability,
                Runnable onGranted,
                Runnable onCancelled
        ) {
            if (granted) {
                return true;
            }
            requests.add(capability);
            pendingGrant = onGranted;
            pendingCancel = onCancelled;
            return false;
        }

        void grant() {
            granted = true;
            Runnable action = pendingGrant;
            pendingGrant = null;
            pendingCancel = null;
            action.run();
        }

        void deny() {
            Runnable action = pendingCancel;
            pendingGrant = null;
            pendingCancel = null;
            action.run();
        }
    }
}
