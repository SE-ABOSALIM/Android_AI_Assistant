package com.example.anroidaiassistant;

import com.example.anroidaiassistant.util.TextNormalizer;

final class CloseAppRetryController {
    interface ForegroundPackageReader {
        String readPackageName();
    }

    interface BackPerformer {
        void performBack();
    }

    interface DelayScheduler {
        void postDelayed(Runnable runnable, long delayMillis);
    }

    interface FailureFeedback {
        void showFailure();
    }

    interface WindowSnapshotReader {
        CloseAppWindowSnapshot capture(String expectedPackageName);
    }

    private static final int BACK_RETRY_DELAY_MS = 450;
    private static final int MAX_BACK_ATTEMPTS = 15;

    private final ForegroundPackageReader foregroundPackageReader;
    private final BackPerformer backPerformer;
    private final DelayScheduler delayScheduler;
    private final FailureFeedback failureFeedback;
    private final WindowSnapshotReader windowSnapshotReader;

    CloseAppRetryController(
            ForegroundPackageReader foregroundPackageReader,
            BackPerformer backPerformer,
            DelayScheduler delayScheduler,
            FailureFeedback failureFeedback,
            WindowSnapshotReader windowSnapshotReader
    ) {
        this.foregroundPackageReader = foregroundPackageReader;
        this.backPerformer = backPerformer;
        this.delayScheduler = delayScheduler;
        this.failureFeedback = failureFeedback;
        this.windowSnapshotReader = windowSnapshotReader;
    }

    void performCloseApp() {
        String initialPackageName = foregroundPackageReader.readPackageName();
        if (!TextNormalizer.hasText(initialPackageName)) {
            backPerformer.performBack();
            return;
        }

        performBackAttempt(initialPackageName, 1);
    }

    private void performBackAttempt(String initialPackageName, int attempt) {
        CloseAppWindowSnapshot pre = windowSnapshotReader.capture(initialPackageName);
        backPerformer.performBack();
        delayScheduler.postDelayed(() -> {
            String currentPackageName = foregroundPackageReader.readPackageName();
            if (!initialPackageName.equals(currentPackageName)) {
                return;
            }
            CloseAppWindowSnapshot post = windowSnapshotReader.capture(initialPackageName);
            if (CloseAppBlockingModalDetector.isBlockingModal(initialPackageName, pre, post)) {
                // End this operation; the user's dialog choice must never resume it.
                return;
            }
            if (attempt >= MAX_BACK_ATTEMPTS) {
                failureFeedback.showFailure();
                return;
            }
            performBackAttempt(initialPackageName, attempt + 1);
        }, BACK_RETRY_DELAY_MS);
    }
}
