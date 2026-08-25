package com.example.anroidaiassistant.permissions;

public final class FeaturePermissionCoordinator {
    static final long MAX_PENDING_AGE_MILLIS = 120_000L;

    public interface Clock {
        long nowMillis();
    }

    public interface Host {
        boolean isGranted(AssistantCapability capability);

        boolean isPermanentlyDenied(AssistantCapability capability);

        void showExplanation(
                AssistantCapability capability,
                boolean permanentlyDenied,
                Runnable onAccepted,
                Runnable onCancelled
        );

        void requestRuntimePermission(AssistantCapability capability);

        void openSpecialAccessSettings(AssistantCapability capability);

        void openApplicationSettings(AssistantCapability capability);

        void showDeniedFeedback(AssistantCapability capability);
    }

    private final Clock clock;
    private long nextToken;
    private PendingAction pendingAction;

    public FeaturePermissionCoordinator(Clock clock) {
        this.clock = clock;
    }

    public boolean ensureGranted(
            Host host,
            AssistantCapability capability,
            Runnable onGranted,
            Runnable onCancelled
    ) {
        if (host.isGranted(capability)) {
            return true;
        }

        PendingAction replaced;
        PendingAction created;
        synchronized (this) {
            replaced = takePendingLocked();
            created = new PendingAction(
                    ++nextToken,
                    capability,
                    clock.nowMillis(),
                    onGranted,
                    onCancelled
            );
            pendingAction = created;
        }
        cancel(replaced);

        boolean permanentlyDenied = host.isPermanentlyDenied(capability);
        host.showExplanation(
                capability,
                permanentlyDenied,
                () -> onExplanationAccepted(host, created.token),
                () -> onExplanationCancelled(created.token)
        );
        return false;
    }

    public void onRuntimePermissionResult(
            Host host,
            AssistantCapability capability,
            boolean granted
    ) {
        PendingAction completed;
        boolean allowed = granted || host.isGranted(capability);
        synchronized (this) {
            if (!matchesLocked(capability, PendingPhase.RUNTIME_PERMISSION)) {
                return;
            }
            completed = takePendingLocked();
        }
        if (allowed) {
            grant(completed);
        } else {
            host.showDeniedFeedback(capability);
            cancel(completed);
        }
    }

    public void onSettingsReturned(Host host) {
        PendingAction completed;
        boolean allowed;
        synchronized (this) {
            if (pendingAction == null || pendingAction.phase != PendingPhase.SETTINGS) {
                return;
            }
            if (isExpiredLocked(pendingAction)) {
                completed = takePendingLocked();
                allowed = false;
            } else {
                allowed = host.isGranted(pendingAction.capability);
                completed = takePendingLocked();
            }
        }
        if (allowed) {
            grant(completed);
        } else {
            host.showDeniedFeedback(completed.capability);
            cancel(completed);
        }
    }

    public synchronized boolean hasPendingAction() {
        return pendingAction != null;
    }

    public synchronized AssistantCapability pendingCapability() {
        return pendingAction == null ? null : pendingAction.capability;
    }

    public void cancelPendingAction() {
        PendingAction cancelled;
        synchronized (this) {
            cancelled = takePendingLocked();
        }
        cancel(cancelled);
    }

    private void onExplanationAccepted(Host host, long token) {
        AssistantCapability capability;
        PendingAction expired = null;
        synchronized (this) {
            if (pendingAction == null || pendingAction.token != token) {
                return;
            }
            if (isExpiredLocked(pendingAction)) {
                expired = takePendingLocked();
                capability = null;
            } else {
                capability = pendingAction.capability;
            }
        }
        if (expired != null) {
            cancel(expired);
            return;
        }

        if (host.isGranted(capability)) {
            PendingAction completed;
            synchronized (this) {
                completed = pendingAction != null && pendingAction.token == token
                        ? takePendingLocked()
                        : null;
            }
            grant(completed);
            return;
        }

        if (capability.isRuntimePermission()) {
            synchronized (this) {
                if (pendingAction == null || pendingAction.token != token) {
                    return;
                }
                pendingAction.phase = host.isPermanentlyDenied(capability)
                        ? PendingPhase.SETTINGS
                        : PendingPhase.RUNTIME_PERMISSION;
            }
            if (host.isPermanentlyDenied(capability)) {
                host.openApplicationSettings(capability);
            } else {
                host.requestRuntimePermission(capability);
            }
            return;
        }

        if (capability.isSpecialAccess()) {
            synchronized (this) {
                if (pendingAction == null || pendingAction.token != token) {
                    return;
                }
                pendingAction.phase = PendingPhase.SETTINGS;
            }
            host.openSpecialAccessSettings(capability);
            return;
        }

        onExplanationCancelled(token);
    }

    private void onExplanationCancelled(long token) {
        PendingAction cancelled;
        synchronized (this) {
            if (pendingAction == null || pendingAction.token != token) {
                return;
            }
            cancelled = takePendingLocked();
        }
        cancel(cancelled);
    }

    private boolean matchesLocked(
            AssistantCapability capability,
            PendingPhase phase
    ) {
        if (pendingAction == null
                || pendingAction.capability != capability
                || pendingAction.phase != phase) {
            return false;
        }
        if (!isExpiredLocked(pendingAction)) {
            return true;
        }
        PendingAction expired = takePendingLocked();
        cancel(expired);
        return false;
    }

    private boolean isExpiredLocked(PendingAction action) {
        return clock.nowMillis() - action.createdAtMillis > MAX_PENDING_AGE_MILLIS;
    }

    private PendingAction takePendingLocked() {
        PendingAction result = pendingAction;
        pendingAction = null;
        return result;
    }

    private void grant(PendingAction action) {
        if (action != null && action.onGranted != null) {
            action.onGranted.run();
        }
    }

    private void cancel(PendingAction action) {
        if (action != null && action.onCancelled != null) {
            action.onCancelled.run();
        }
    }

    private enum PendingPhase {
        EXPLANATION,
        RUNTIME_PERMISSION,
        SETTINGS
    }

    private static final class PendingAction {
        final long token;
        final AssistantCapability capability;
        final long createdAtMillis;
        final Runnable onGranted;
        final Runnable onCancelled;
        PendingPhase phase = PendingPhase.EXPLANATION;

        PendingAction(
                long token,
                AssistantCapability capability,
                long createdAtMillis,
                Runnable onGranted,
                Runnable onCancelled
        ) {
            this.token = token;
            this.capability = capability;
            this.createdAtMillis = createdAtMillis;
            this.onGranted = onGranted;
            this.onCancelled = onCancelled;
        }
    }
}
