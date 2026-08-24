package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class CloseAppRetryControllerTest {
    @Test
    public void packageChangesAfterFirstBack_stopsAtOneBack() {
        TestHarness harness = new TestHarness(Arrays.asList("package.a", "package.b"));

        harness.controller.performCloseApp();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(1, harness.scheduler.pendingCount());
        harness.scheduler.runNext();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(0, harness.scheduler.pendingCount());
        assertEquals(Collections.singletonList(450L), harness.scheduler.delays);
        assertEquals(0, harness.failureFeedback.failureCount);
    }

    @Test
    public void packageRemainsForeground_stopsAtCurrentBound() {
        TestHarness harness = new TestHarness(Collections.singletonList("package.a"));

        harness.controller.performCloseApp();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(1, harness.scheduler.pendingCount());
        harness.scheduler.runUntilIdle(20);

        assertEquals(15, harness.backPerformer.backCount);
        assertEquals(15, harness.scheduler.executedCount);
        assertEquals(15, harness.scheduler.delays.size());
        assertTrue(allDelaysEqual(harness.scheduler.delays, 450L));
        assertEquals(1, harness.failureFeedback.failureCount);
        assertEquals(0, harness.scheduler.pendingCount());
    }

    @Test
    public void missingInitialPackage_performsOneBackWithoutRetryOrFeedback() {
        TestHarness harness = new TestHarness(Collections.singletonList(null));

        harness.controller.performCloseApp();

        assertEquals(1, harness.packageReader.readCount);
        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(0, harness.scheduler.pendingCount());
        assertTrue(harness.scheduler.delays.isEmpty());
        assertEquals(0, harness.failureFeedback.failureCount);
    }

    @Test
    public void packageChangesAfterSeveralRetries_stopsImmediatelyAfterObservedChange() {
        TestHarness harness = new TestHarness(Arrays.asList(
                "package.a",
                "package.a",
                "package.a",
                "package.b"
        ));

        harness.controller.performCloseApp();
        harness.scheduler.runUntilIdle(10);

        assertEquals(3, harness.backPerformer.backCount);
        assertEquals(3, harness.scheduler.executedCount);
        assertEquals(Arrays.asList(450L, 450L, 450L), harness.scheduler.delays);
        assertEquals(0, harness.failureFeedback.failureCount);
        assertEquals(0, harness.scheduler.pendingCount());
    }

    private static boolean allDelaysEqual(List<Long> delays, long expectedDelay) {
        for (long delay : delays) {
            if (delay != expectedDelay) {
                return false;
            }
        }
        return true;
    }

    private static final class TestHarness {
        private final FakePackageReader packageReader;
        private final FakeBackPerformer backPerformer = new FakeBackPerformer();
        private final FakeDelayScheduler scheduler = new FakeDelayScheduler();
        private final FakeFailureFeedback failureFeedback = new FakeFailureFeedback();
        private final CloseAppRetryController controller;

        private TestHarness(List<String> packageNames) {
            packageReader = new FakePackageReader(packageNames);
            controller = new CloseAppRetryController(
                    packageReader,
                    backPerformer,
                    scheduler,
                    failureFeedback
            );
        }
    }

    private static final class FakePackageReader
            implements CloseAppRetryController.ForegroundPackageReader {
        private final List<String> packageNames;
        private int nextIndex;
        private int readCount;

        private FakePackageReader(List<String> packageNames) {
            this.packageNames = packageNames;
        }

        @Override
        public String readPackageName() {
            readCount++;
            if (nextIndex < packageNames.size()) {
                return packageNames.get(nextIndex++);
            }
            return packageNames.get(packageNames.size() - 1);
        }
    }

    private static final class FakeBackPerformer
            implements CloseAppRetryController.BackPerformer {
        private int backCount;

        @Override
        public void performBack() {
            backCount++;
        }
    }

    private static final class FakeDelayScheduler
            implements CloseAppRetryController.DelayScheduler {
        private final Deque<Runnable> pendingTasks = new ArrayDeque<>();
        private final List<Long> delays = new ArrayList<>();
        private int executedCount;

        @Override
        public void postDelayed(Runnable runnable, long delayMillis) {
            pendingTasks.addLast(runnable);
            delays.add(delayMillis);
        }

        private int pendingCount() {
            return pendingTasks.size();
        }

        private void runNext() {
            assertFalse("Expected a scheduled CLOSE_APP check", pendingTasks.isEmpty());
            executedCount++;
            pendingTasks.removeFirst().run();
        }

        private void runUntilIdle(int maximumTasks) {
            int remaining = maximumTasks;
            while (!pendingTasks.isEmpty() && remaining > 0) {
                runNext();
                remaining--;
            }
            assertTrue("CLOSE_APP retry work did not terminate", pendingTasks.isEmpty());
        }
    }

    private static final class FakeFailureFeedback
            implements CloseAppRetryController.FailureFeedback {
        private int failureCount;

        @Override
        public void showFailure() {
            failureCount++;
        }
    }
}
