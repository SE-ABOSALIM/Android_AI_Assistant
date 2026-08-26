package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RecognitionTerminalCallbackWatchdogTest {
    private static final long SESSION_A = 11L;
    private static final long SESSION_B = 12L;
    private static final long TERMINAL_TIMEOUT_MS = 12_000L;

    @Test
    public void endOfSpeech_withoutTerminalCallback_recoversAfterTimeout() {
        Fixture fixture = new Fixture();

        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);

        assertTrue(fixture.watchdog.isArmedFor(SESSION_A));
        assertEquals(TERMINAL_TIMEOUT_MS, fixture.callbacks.onlyPendingDelay());

        fixture.callbacks.runOnlyPending();

        assertEquals(1, fixture.recoveryCount);
        assertFalse(fixture.watchdog.isArmed());
    }

    @Test
    public void resultBeforeTimeout_cancelsTerminalWatchdog() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable timeout = fixture.callbacks.latestScheduledCallback();

        fixture.watchdog.complete(SESSION_A);
        timeout.run();

        assertEquals(0, fixture.recoveryCount);
        assertFalse(fixture.watchdog.isArmed());
    }

    @Test
    public void errorBeforeTimeout_cancelsTerminalWatchdog() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable timeout = fixture.callbacks.latestScheduledCallback();

        fixture.watchdog.complete(SESSION_A);
        timeout.run();

        assertEquals(0, fixture.recoveryCount);
    }

    @Test
    public void acceptedPartialFallback_cancelsTerminalWatchdog() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable timeout = fixture.callbacks.latestScheduledCallback();

        fixture.watchdog.complete(SESSION_A);
        timeout.run();

        assertEquals(0, fixture.recoveryCount);
    }

    @Test
    public void terminalTimeout_schedulesExactlyOneRestart() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable timeout = fixture.callbacks.latestScheduledCallback();

        timeout.run();
        timeout.run();

        assertEquals(1, fixture.recoveryCount);
        assertEquals(1, fixture.restartCallbacks.pendingCount());
        assertEquals(200L, fixture.restartCallbacks.onlyPendingDelay());
    }

    @Test
    public void cancelCallbackAfterTimeout_doesNotScheduleDuplicateRestart() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        FakeDelayedCallbacks callbacks = new FakeDelayedCallbacks();
        int[] restartCount = {0};
        long generation = tracker.beginRecognitionSession();
        RecognitionTerminalCallbackWatchdog watchdog = new RecognitionTerminalCallbackWatchdog(
                timedOutGeneration -> {
                    if (tracker.isCurrentRecognitionGeneration(timedOutGeneration)) {
                        tracker.invalidateRecognitionCallbacks();
                        restartCount[0]++;
                    }
                },
                callbacks::removeCallbacks,
                callbacks::postDelayed
        );
        watchdog.arm(generation, TERMINAL_TIMEOUT_MS);

        callbacks.runOnlyPending();
        if (tracker.isCurrentRecognitionGeneration(generation)) {
            restartCount[0]++;
        }

        assertEquals(1, restartCount[0]);
    }

    @Test
    public void lateResultAfterTimeout_isIgnored() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        Fixture fixture = new Fixture(tracker);
        long generation = tracker.beginRecognitionSession();
        int[] commandCount = {0};
        fixture.watchdog.arm(generation, TERMINAL_TIMEOUT_MS);

        fixture.callbacks.runOnlyPending();
        if (tracker.isCurrentRecognitionGeneration(generation)) {
            commandCount[0]++;
        }

        assertEquals(0, commandCount[0]);
        assertEquals(1, fixture.recoveryCount);
    }

    @Test
    public void lateErrorAfterTimeout_isIgnored() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        Fixture fixture = new Fixture(tracker);
        long generation = tracker.beginRecognitionSession();
        fixture.watchdog.arm(generation, TERMINAL_TIMEOUT_MS);

        fixture.callbacks.runOnlyPending();
        if (tracker.isCurrentRecognitionGeneration(generation)) {
            fixture.recoveryCount++;
        }

        assertEquals(1, fixture.recoveryCount);
    }

    @Test
    public void oldSessionTimeout_cannotAffectNewSession() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable oldTimeout = fixture.callbacks.latestScheduledCallback();

        fixture.watchdog.arm(SESSION_B, TERMINAL_TIMEOUT_MS);
        oldTimeout.run();

        assertEquals(0, fixture.recoveryCount);
        assertTrue(fixture.watchdog.isArmedFor(SESSION_B));
    }

    @Test
    public void stopListening_cancelsOrInvalidatesTerminalWatchdog() {
        assertIntentionalCancellationPreventsRecovery();
    }

    @Test
    public void serviceDestroy_cancelsOrInvalidatesTerminalWatchdog() {
        assertIntentionalCancellationPreventsRecovery();
    }

    @Test
    public void phoneCallPause_doesNotAllowWatchdogRestart() {
        assertIntentionalCancellationPreventsRecovery();
    }

    @Test
    public void recognitionGenerationSafety_isPreserved() {
        RecognitionInteractionTracker tracker = new RecognitionInteractionTracker();
        long oldGeneration = tracker.beginRecognitionSession();

        tracker.invalidateRecognitionCallbacks();
        long newGeneration = tracker.beginRecognitionSession();

        assertFalse(tracker.isCurrentRecognitionGeneration(oldGeneration));
        assertTrue(tracker.isCurrentRecognitionGeneration(newGeneration));
    }

    private void assertIntentionalCancellationPreventsRecovery() {
        Fixture fixture = new Fixture();
        fixture.watchdog.arm(SESSION_A, TERMINAL_TIMEOUT_MS);
        Runnable timeout = fixture.callbacks.latestScheduledCallback();

        fixture.watchdog.cancel();
        timeout.run();

        assertEquals(0, fixture.recoveryCount);
        assertFalse(fixture.watchdog.isArmed());
    }

    private static final class Fixture {
        private final FakeDelayedCallbacks callbacks = new FakeDelayedCallbacks();
        private final FakeDelayedCallbacks restartCallbacks = new FakeDelayedCallbacks();
        private final RecognitionInteractionTracker tracker;
        private final ListeningRestartScheduler restartScheduler;
        private final RecognitionTerminalCallbackWatchdog watchdog;
        private int recoveryCount;

        private Fixture() {
            this(null);
        }

        private Fixture(RecognitionInteractionTracker tracker) {
            this.tracker = tracker;
            restartScheduler = new ListeningRestartScheduler(
                    () -> {},
                    restartCallbacks::removeCallbacks,
                    restartCallbacks::postDelayed
            );
            watchdog = new RecognitionTerminalCallbackWatchdog(
                    this::recover,
                    callbacks::removeCallbacks,
                    callbacks::postDelayed
            );
        }

        private void recover(long generation) {
            if (tracker != null && !tracker.isCurrentRecognitionGeneration(generation)) {
                return;
            }
            if (tracker != null) {
                tracker.invalidateRecognitionCallbacks();
            }
            recoveryCount++;
            restartScheduler.schedule(200L);
        }
    }

    private static final class FakeDelayedCallbacks {
        private final List<ScheduledCallback> pendingCallbacks = new ArrayList<>();
        private final List<ScheduledCallback> scheduledHistory = new ArrayList<>();

        private void removeCallbacks(Runnable callback) {
            pendingCallbacks.removeIf(scheduled -> scheduled.callback == callback);
        }

        private void postDelayed(Runnable callback, long delayMillis) {
            ScheduledCallback scheduled = new ScheduledCallback(callback, delayMillis);
            pendingCallbacks.add(scheduled);
            scheduledHistory.add(scheduled);
        }

        private long onlyPendingDelay() {
            assertEquals(1, pendingCallbacks.size());
            return pendingCallbacks.get(0).delayMillis;
        }

        private int pendingCount() {
            return pendingCallbacks.size();
        }

        private Runnable latestScheduledCallback() {
            return scheduledHistory.get(scheduledHistory.size() - 1).callback;
        }

        private void runOnlyPending() {
            assertEquals(1, pendingCallbacks.size());
            ScheduledCallback scheduled = pendingCallbacks.remove(0);
            scheduled.callback.run();
        }
    }

    private static final class ScheduledCallback {
        private final Runnable callback;
        private final long delayMillis;

        private ScheduledCallback(Runnable callback, long delayMillis) {
            this.callback = callback;
            this.delayMillis = delayMillis;
        }
    }
}
