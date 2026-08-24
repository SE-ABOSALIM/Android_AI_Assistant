package com.example.anroidaiassistant;

final class ListeningRestartScheduler {
    interface CallbackRemover {
        void removeCallbacks(Runnable callback);
    }

    interface DelayedCallbackPoster {
        void postDelayed(Runnable callback, long delayMillis);
    }

    private final Runnable restartCallback;
    private final CallbackRemover callbackRemover;
    private final DelayedCallbackPoster delayedCallbackPoster;

    ListeningRestartScheduler(
            Runnable restartCallback,
            CallbackRemover callbackRemover,
            DelayedCallbackPoster delayedCallbackPoster
    ) {
        this.restartCallback = restartCallback;
        this.callbackRemover = callbackRemover;
        this.delayedCallbackPoster = delayedCallbackPoster;
    }

    void cancel() {
        callbackRemover.removeCallbacks(restartCallback);
    }

    void schedule(long delayMillis) {
        cancel();
        post(delayMillis);
    }

    void post(long delayMillis) {
        delayedCallbackPoster.postDelayed(restartCallback, delayMillis);
    }
}
