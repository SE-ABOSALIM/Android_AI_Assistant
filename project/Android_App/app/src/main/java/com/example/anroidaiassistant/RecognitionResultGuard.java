package com.example.anroidaiassistant;

final class RecognitionResultGuard {
    private boolean handled;

    boolean isHandled() {
        return handled;
    }

    boolean tryMarkHandled() {
        if (handled) {
            return false;
        }
        handled = true;
        return true;
    }

    boolean runIfFirst(Runnable acceptedResultPath) {
        if (!tryMarkHandled()) {
            return false;
        }
        acceptedResultPath.run();
        return true;
    }

    void reset() {
        handled = false;
    }
}
