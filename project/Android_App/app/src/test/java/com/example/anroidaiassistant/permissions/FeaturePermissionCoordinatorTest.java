package com.example.anroidaiassistant.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FeaturePermissionCoordinatorTest {
    @Test
    public void optionalFeatureShowsExplanationBeforePermissionRequest() {
        FakeHost host = new FakeHost();
        FeaturePermissionCoordinator coordinator = coordinator();

        assertFalse(coordinator.ensureGranted(
                host,
                AssistantCapability.CONTACTS,
                () -> {},
                () -> {}
        ));
        assertEquals(List.of("explanation:CONTACTS"), host.events);

        host.acceptExplanation();

        assertEquals(
                List.of("explanation:CONTACTS", "runtime:CONTACTS"),
                host.events
        );
    }

    @Test
    public void specialAccessShowsExplanationBeforeOpeningSettings() {
        FakeHost host = new FakeHost();
        FeaturePermissionCoordinator coordinator = coordinator();

        coordinator.ensureGranted(
                host,
                AssistantCapability.SOUND_MODE,
                () -> {},
                () -> {}
        );
        host.acceptExplanation();

        assertEquals(
                List.of("explanation:SOUND_MODE", "special:SOUND_MODE"),
                host.events
        );
    }

    @Test
    public void permanentlyDeniedPermission_doesNotLoop() {
        FakeHost host = new FakeHost();
        host.permanentlyDenied = true;
        AtomicInteger cancelled = new AtomicInteger();
        FeaturePermissionCoordinator coordinator = coordinator();

        coordinator.ensureGranted(
                host,
                AssistantCapability.CONTACTS,
                () -> {},
                cancelled::incrementAndGet
        );
        host.acceptExplanation();

        assertEquals(
                List.of("explanation:CONTACTS", "app-settings:CONTACTS"),
                host.events
        );
        assertEquals(0, host.runtimeRequests);

        coordinator.onSettingsReturned(host);

        assertEquals(1, cancelled.get());
        assertFalse(coordinator.hasPendingAction());
    }

    @Test
    public void deniedOptionalPermission_cancelsOnlyPendingFeature() {
        FakeHost host = new FakeHost();
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        FeaturePermissionCoordinator coordinator = coordinator();

        coordinator.ensureGranted(
                host,
                AssistantCapability.CAMERA,
                granted::incrementAndGet,
                cancelled::incrementAndGet
        );
        host.acceptExplanation();
        coordinator.onRuntimePermissionResult(host, AssistantCapability.CAMERA, false);

        assertEquals(0, granted.get());
        assertEquals(1, cancelled.get());
        assertEquals(1, host.deniedFeedbackCount);
        assertFalse(coordinator.hasPendingAction());
    }

    @Test
    public void permissionGrantDoesNotDuplicatePendingCommandExecution() {
        FakeHost host = new FakeHost();
        AtomicInteger executions = new AtomicInteger();
        FeaturePermissionCoordinator coordinator = coordinator();

        coordinator.ensureGranted(
                host,
                AssistantCapability.ANSWER_CALL,
                executions::incrementAndGet,
                () -> {}
        );
        host.acceptExplanation();
        host.granted = true;

        coordinator.onRuntimePermissionResult(host, AssistantCapability.ANSWER_CALL, true);
        coordinator.onRuntimePermissionResult(host, AssistantCapability.ANSWER_CALL, true);

        assertEquals(1, executions.get());
        assertFalse(coordinator.hasPendingAction());
    }

    @Test
    public void grantedFeaturePermission_allowsProtectedAction() {
        FakeHost host = new FakeHost();
        host.granted = true;
        AtomicInteger executions = new AtomicInteger();

        assertTrue(coordinator().ensureGranted(
                host,
                AssistantCapability.SYSTEM_SETTINGS,
                executions::incrementAndGet,
                () -> {}
        ));
        assertEquals(0, executions.get());
        assertTrue(host.events.isEmpty());
    }

    @Test
    public void stalePermissionResult_doesNotExecutePendingAction() {
        long[] now = {1_000L};
        FeaturePermissionCoordinator coordinator = new FeaturePermissionCoordinator(() -> now[0]);
        FakeHost host = new FakeHost();
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();

        coordinator.ensureGranted(
                host,
                AssistantCapability.CONTACTS,
                executions::incrementAndGet,
                cancelled::incrementAndGet
        );
        host.acceptExplanation();
        now[0] += FeaturePermissionCoordinator.MAX_PENDING_AGE_MILLIS + 1L;
        coordinator.onRuntimePermissionResult(host, AssistantCapability.CONTACTS, true);

        assertEquals(0, executions.get());
        assertEquals(1, cancelled.get());
        assertFalse(coordinator.hasPendingAction());
    }

    private FeaturePermissionCoordinator coordinator() {
        return new FeaturePermissionCoordinator(() -> 1_000L);
    }

    private static final class FakeHost implements FeaturePermissionCoordinator.Host {
        private final List<String> events = new ArrayList<>();
        private boolean granted;
        private boolean permanentlyDenied;
        private int runtimeRequests;
        private int deniedFeedbackCount;
        private Runnable explanationAccepted;

        @Override
        public boolean isGranted(AssistantCapability capability) {
            return granted;
        }

        @Override
        public boolean isPermanentlyDenied(AssistantCapability capability) {
            return permanentlyDenied;
        }

        @Override
        public void showExplanation(
                AssistantCapability capability,
                boolean permanentlyDenied,
                Runnable onAccepted,
                Runnable onCancelled
        ) {
            events.add("explanation:" + capability.name());
            explanationAccepted = onAccepted;
        }

        @Override
        public void requestRuntimePermission(AssistantCapability capability) {
            runtimeRequests++;
            events.add("runtime:" + capability.name());
        }

        @Override
        public void openSpecialAccessSettings(AssistantCapability capability) {
            events.add("special:" + capability.name());
        }

        @Override
        public void openApplicationSettings(AssistantCapability capability) {
            events.add("app-settings:" + capability.name());
        }

        @Override
        public void showDeniedFeedback(AssistantCapability capability) {
            deniedFeedbackCount++;
        }

        void acceptExplanation() {
            explanationAccepted.run();
        }
    }
}
