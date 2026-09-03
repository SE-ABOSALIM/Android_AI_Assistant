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

    @Test
    public void newSmallApplicationWindow_stopsSilentlyAfterFirstBack() {
        TestHarness harness = new TestHarness(Collections.singletonList("package.a"),
                Arrays.asList(fullWindow(10), dialogWindow(11)));

        harness.controller.performCloseApp();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(Collections.singletonList(0), harness.snapshotReader.backCountsAtCapture);
        harness.scheduler.runNext();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(0, harness.scheduler.pendingCount());
        assertEquals(0, harness.failureFeedback.failureCount);
        assertEquals(Collections.singletonList(450L), harness.scheduler.delays);
        assertEquals(Arrays.asList(0, 1), harness.snapshotReader.backCountsAtCapture);
    }

    @Test
    public void differentFullScreenWindow_continuesNormalRetry() {
        assertRetryContinues(fullWindow(10), fullWindow(11));
    }

    @Test
    public void sameWindow_continuesNormalRetry() {
        assertRetryContinues(fullWindow(10), fullWindow(10));
    }

    @Test
    public void changedPackage_stopsWithoutReadingPostSnapshot() {
        TestHarness harness = new TestHarness(Arrays.asList("package.a", "package.b"),
                Arrays.asList(fullWindow(10), dialogWindow(11)));

        harness.controller.performCloseApp();
        harness.scheduler.runNext();

        assertEquals(1, harness.backPerformer.backCount);
        assertEquals(0, harness.scheduler.pendingCount());
        assertEquals(0, harness.failureFeedback.failureCount);
        assertEquals(1, harness.snapshotReader.backCountsAtCapture.size());
    }

    @Test
    public void emptyPostBounds_continuesNormalRetry() {
        assertRetryContinues(fullWindow(10),
                new CloseAppWindowSnapshot("package.a", 11, true, true, true, 0, 0, 0, 0));
    }

    @Test
    public void missingSnapshot_continuesNormalRetry() {
        assertRetryContinues(null, dialogWindow(11));
        assertRetryContinues(fullWindow(10), null);
    }

    @Test
    public void modalAfterLaterBack_stopsAtThatAttempt() {
        TestHarness harness = new TestHarness(Collections.singletonList("package.a"),
                Arrays.asList(fullWindow(10), fullWindow(11), fullWindow(11), dialogWindow(12)));

        harness.controller.performCloseApp();
        harness.scheduler.runUntilIdle(20);

        assertEquals(2, harness.backPerformer.backCount);
        assertEquals(2, harness.scheduler.executedCount);
        assertEquals(Arrays.asList(450L, 450L), harness.scheduler.delays);
        assertEquals(0, harness.failureFeedback.failureCount);
        assertEquals(Arrays.asList(0, 1, 1, 2), harness.snapshotReader.backCountsAtCapture);
    }

    @Test
    public void newCommandAfterModalStop_startsFreshOperation() {
        TestHarness harness = new TestHarness(Collections.singletonList("package.a"),
                Arrays.asList(fullWindow(10), dialogWindow(11), fullWindow(10), dialogWindow(12)));

        harness.controller.performCloseApp();
        harness.scheduler.runNext();
        assertEquals(0, harness.scheduler.pendingCount());

        harness.controller.performCloseApp();
        assertEquals(2, harness.backPerformer.backCount);
        harness.scheduler.runNext();

        assertEquals(2, harness.backPerformer.backCount);
        assertEquals(0, harness.scheduler.pendingCount());
        assertEquals(0, harness.failureFeedback.failureCount);
    }

    private static void assertRetryContinues(CloseAppWindowSnapshot pre, CloseAppWindowSnapshot post) {
        TestHarness harness = new TestHarness(Collections.singletonList("package.a"),
                Arrays.asList(pre, post));
        harness.controller.performCloseApp();
        harness.scheduler.runNext();

        assertEquals(2, harness.backPerformer.backCount);
        assertEquals(1, harness.scheduler.pendingCount());
        assertEquals(Arrays.asList(450L, 450L), harness.scheduler.delays);
        harness.scheduler.runUntilIdle(20);
        assertEquals(15, harness.backPerformer.backCount);
        assertEquals(1, harness.failureFeedback.failureCount);
    }

    private static CloseAppWindowSnapshot fullWindow(int id) {
        return new CloseAppWindowSnapshot("package.a", id, true, true, true, 0, 0, 1080, 2400);
    }

    private static CloseAppWindowSnapshot dialogWindow(int id) {
        return new CloseAppWindowSnapshot("package.a", id, true, true, true, 28, 912, 1052, 1425);
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
        private final FakeSnapshotReader snapshotReader;
        private final CloseAppRetryController controller;

        private TestHarness(List<String> packageNames) {
            this(packageNames, Collections.singletonList(null));
        }

        private TestHarness(List<String> packageNames, List<CloseAppWindowSnapshot> snapshots) {
            packageReader = new FakePackageReader(packageNames);
            snapshotReader = new FakeSnapshotReader(snapshots, backPerformer);
            controller = new CloseAppRetryController(
                    packageReader,
                    backPerformer,
                    scheduler,
                    failureFeedback,
                    snapshotReader
            );
        }
    }

    private static final class FakeSnapshotReader implements CloseAppRetryController.WindowSnapshotReader {
        private final List<CloseAppWindowSnapshot> snapshots;
        private final FakeBackPerformer backPerformer;
        private final List<Integer> backCountsAtCapture = new ArrayList<>();
        private int nextIndex;

        private FakeSnapshotReader(List<CloseAppWindowSnapshot> snapshots, FakeBackPerformer backPerformer) {
            this.snapshots = snapshots;
            this.backPerformer = backPerformer;
        }

        @Override
        public CloseAppWindowSnapshot capture(String expectedPackageName) {
            assertEquals("package.a", expectedPackageName);
            backCountsAtCapture.add(backPerformer.backCount);
            return snapshots.get(Math.min(nextIndex++, snapshots.size() - 1));
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
