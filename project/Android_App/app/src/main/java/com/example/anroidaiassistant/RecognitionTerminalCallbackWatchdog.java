package com.example.anroidaiassistant;

final class RecognitionTerminalCallbackWatchdog {
    interface TimeoutCallback {
        void onTimeout(long recognitionGeneration);
    }

    interface CallbackRemover {
        void removeCallbacks(Runnable callback);
    }

    interface DelayedCallbackPoster {
        void postDelayed(Runnable callback, long delayMillis);
    }

    private static final long NO_GENERATION = -1L;

    private final TimeoutCallback timeoutCallback;
    private final CallbackRemover callbackRemover;
    private final DelayedCallbackPoster delayedCallbackPoster;
    private long callbackToken;
    private long armedGeneration = NO_GENERATION;
    private Runnable pendingCallback;

    RecognitionTerminalCallbackWatchdog(
            TimeoutCallback timeoutCallback,
            CallbackRemover callbackRemover,
            DelayedCallbackPoster delayedCallbackPoster
    ) {
        this.timeoutCallback = timeoutCallback;
        this.callbackRemover = callbackRemover;
        this.delayedCallbackPoster = delayedCallbackPoster;
    }

    void arm(long recognitionGeneration, long delayMillis) {
        cancel();

        long token = ++callbackToken;
        armedGeneration = recognitionGeneration;
        pendingCallback = () -> handleTimeout(recognitionGeneration, token);
        delayedCallbackPoster.postDelayed(pendingCallback, delayMillis);
    }

    void complete(long recognitionGeneration) {
        if (armedGeneration == recognitionGeneration) {
            cancel();
        }
    }

    void cancel() {
        callbackToken++;
        Runnable callback = pendingCallback;
        pendingCallback = null;
        armedGeneration = NO_GENERATION;
        if (callback != null) {
            callbackRemover.removeCallbacks(callback);
        }
    }

    boolean isArmed() {
        return pendingCallback != null;
    }

    boolean isArmedFor(long recognitionGeneration) {
        return isArmed() && armedGeneration == recognitionGeneration;
    }

    private void handleTimeout(long recognitionGeneration, long token) {
        if (pendingCallback == null
                || callbackToken != token
                || armedGeneration != recognitionGeneration) {
            return;
        }

        pendingCallback = null;
        armedGeneration = NO_GENERATION;
        timeoutCallback.onTimeout(recognitionGeneration);
    }
}
