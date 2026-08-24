package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RecognitionSessionCharacterizationTest {
    @Test
    public void partialThenFinal_sameRecognitionSession_sendsOnePrediction() {
        RecognitionResultGuard resultGuard = new RecognitionResultGuard();
        int[] predictionCount = {0};

        boolean partialAccepted = resultGuard.runIfFirst(() -> predictionCount[0]++);
        boolean finalAccepted = resultGuard.runIfFirst(() -> predictionCount[0]++);

        assertTrue(partialAccepted);
        assertFalse(finalAccepted);
        assertEquals(1, predictionCount[0]);
    }

    @Test
    public void onResultsTwice_sameRecognitionSession_sendsOnePrediction() {
        RecognitionResultGuard resultGuard = new RecognitionResultGuard();
        int[] predictionCount = {0};

        boolean firstFinalAccepted = resultGuard.runIfFirst(() -> predictionCount[0]++);
        boolean secondFinalAccepted = resultGuard.runIfFirst(() -> predictionCount[0]++);

        assertTrue(firstFinalAccepted);
        assertFalse(secondFinalAccepted);
        assertEquals(1, predictionCount[0]);
    }

    @Test
    public void stopContinuousListening_removesScheduledRestart() {
        FakeDelayedCallbacks callbacks = new FakeDelayedCallbacks();
        int[] restartCount = {0};
        ListeningRestartScheduler restartScheduler = new ListeningRestartScheduler(
                () -> restartCount[0]++,
                callbacks::removeCallbacks,
                callbacks::postDelayed
        );

        restartScheduler.schedule(200L);
        assertEquals(1, callbacks.pendingCount());
        assertEquals(200L, callbacks.pendingCallbacks.get(0).delayMillis);

        restartScheduler.cancel();
        callbacks.runAll();

        assertEquals(0, restartCount[0]);
        assertEquals(0, callbacks.pendingCount());
    }

    @Test
    public void newRecognitionSession_resetAllowsNextPrediction() {
        RecognitionResultGuard resultGuard = new RecognitionResultGuard();
        int[] predictionCount = {0};

        resultGuard.runIfFirst(() -> predictionCount[0]++);
        resultGuard.reset();
        boolean nextSessionAccepted = resultGuard.runIfFirst(() -> predictionCount[0]++);

        assertTrue(nextSessionAccepted);
        assertEquals(2, predictionCount[0]);
    }

    private static final class FakeDelayedCallbacks {
        private final List<ScheduledCallback> pendingCallbacks = new ArrayList<>();

        private void removeCallbacks(Runnable callback) {
            pendingCallbacks.removeIf(scheduled -> scheduled.callback == callback);
        }

        private void postDelayed(Runnable callback, long delayMillis) {
            pendingCallbacks.add(new ScheduledCallback(callback, delayMillis));
        }

        private int pendingCount() {
            return pendingCallbacks.size();
        }

        private void runAll() {
            List<ScheduledCallback> callbacksToRun = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
            for (ScheduledCallback scheduled : callbacksToRun) {
                scheduled.callback.run();
            }
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
