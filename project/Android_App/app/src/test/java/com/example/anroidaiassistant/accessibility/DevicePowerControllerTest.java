package com.example.anroidaiassistant.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class DevicePowerControllerTest {
    @Test
    public void powerDialogOpenFailure_returnsFalseAndSchedulesNothing() {
        FakeNode powerOffNode = new FakeNode("Power off")
                .packageName("com.android.systemui")
                .clickable();
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer(false);
        FakeRootNodeReader rootNodeReader = new FakeRootNodeReader(powerOffNode);
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        DevicePowerController controller = new DevicePowerController(
                globalActions,
                rootNodeReader,
                scheduler
        );

        boolean result = controller.perform(DevicePowerController.Action.POWER_OFF);

        assertFalse(result);
        assertEquals(
                Collections.singletonList(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),
                globalActions.actions
        );
        assertEquals(0, rootNodeReader.readCount);
        assertEquals(0, powerOffNode.clickCount);
        assertEquals(0, scheduler.pendingCount());
        assertTrue(scheduler.delays.isEmpty());
    }

    @Test
    public void powerMenuUnavailable_retriesToCurrentBoundAndStops() {
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer(true);
        FakeRootNodeReader rootNodeReader = new FakeRootNodeReader(null);
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        DevicePowerController controller = new DevicePowerController(
                globalActions,
                rootNodeReader,
                scheduler
        );

        assertTrue(controller.perform(DevicePowerController.Action.POWER_OFF));
        scheduler.runUntilIdle(10);

        assertEquals(6, rootNodeReader.readCount);
        assertEquals(6, scheduler.executedCount);
        assertEquals(6, scheduler.delays.size());
        assertTrue(allDelaysEqual(scheduler.delays, 450L));
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void unmatchedMenu_exhaustsWithoutClicking() {
        FakeNode unmatchedMenu = new FakeNode("Emergency")
                .packageName("com.android.systemui")
                .clickable();
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer(true);
        FakeRootNodeReader rootNodeReader = new FakeRootNodeReader(unmatchedMenu);
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        DevicePowerController controller = new DevicePowerController(
                globalActions,
                rootNodeReader,
                scheduler
        );

        assertTrue(controller.perform(DevicePowerController.Action.RESTART));
        scheduler.runUntilIdle(10);

        assertEquals(0, unmatchedMenu.clickCount);
        assertEquals(6, rootNodeReader.readCount);
        assertEquals(6, scheduler.executedCount);
        assertEquals(6, scheduler.delays.size());
        assertTrue(allDelaysEqual(scheduler.delays, 450L));
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void matchingNode_clicksClickableAncestorAccordingToCurrentLogic() {
        FakeNode clickableAncestor = new FakeNode(null)
                .packageName("com.android.globalactions")
                .clickable();
        FakeNode matchingChild = new FakeNode("Power off");
        clickableAncestor.addChild(matchingChild);
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer(true);
        FakeRootNodeReader rootNodeReader = new FakeRootNodeReader(clickableAncestor);
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        DevicePowerController controller = new DevicePowerController(
                globalActions,
                rootNodeReader,
                scheduler
        );

        assertTrue(controller.perform(DevicePowerController.Action.POWER_OFF));
        scheduler.runNext();

        assertEquals(1, clickableAncestor.clickCount);
        assertEquals(0, matchingChild.clickCount);
    }

    private static boolean allDelaysEqual(List<Long> delays, long expectedDelay) {
        for (long delay : delays) {
            if (delay != expectedDelay) {
                return false;
            }
        }
        return true;
    }

    private static final class FakeGlobalActionPerformer
            implements DevicePowerController.GlobalActionPerformer {
        private final boolean result;
        private final List<Integer> actions = new ArrayList<>();

        private FakeGlobalActionPerformer(boolean result) {
            this.result = result;
        }

        @Override
        public boolean perform(int action) {
            actions.add(action);
            return result;
        }
    }

    private static final class FakeRootNodeReader
            implements DevicePowerController.RootNodeReader {
        private final AccessibilityNodeInfo rootNode;
        private int readCount;

        private FakeRootNodeReader(AccessibilityNodeInfo rootNode) {
            this.rootNode = rootNode;
        }

        @Override
        public AccessibilityNodeInfo readRootNode() {
            readCount++;
            return rootNode;
        }
    }

    private static final class FakeDelayScheduler
            implements DevicePowerController.DelayScheduler {
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
            assertFalse("Expected scheduled power-menu work", pendingTasks.isEmpty());
            executedCount++;
            pendingTasks.removeFirst().run();
        }

        private void runUntilIdle(int maximumTasks) {
            int remaining = maximumTasks;
            while (!pendingTasks.isEmpty() && remaining > 0) {
                runNext();
                remaining--;
            }
            assertTrue("Power-menu retries did not terminate", pendingTasks.isEmpty());
        }
    }

    private static final class FakeNode extends AccessibilityNodeInfo {
        private final CharSequence text;
        private final List<AccessibilityNodeInfo> children = new ArrayList<>();
        private CharSequence packageName;
        private AccessibilityNodeInfo parent;
        private boolean clickable;
        private int clickCount;

        private FakeNode(CharSequence text) {
            this.text = text;
        }

        private FakeNode packageName(CharSequence value) {
            packageName = value;
            return this;
        }

        private FakeNode clickable() {
            clickable = true;
            return this;
        }

        private void addChild(FakeNode child) {
            child.parent = this;
            children.add(child);
        }

        @Override
        public CharSequence getPackageName() {
            return packageName;
        }

        @Override
        public CharSequence getText() {
            return text;
        }

        @Override
        public CharSequence getContentDescription() {
            return null;
        }

        @Override
        public CharSequence getHintText() {
            return null;
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

        @Override
        public AccessibilityNodeInfo getChild(int index) {
            return children.get(index);
        }

        @Override
        public AccessibilityNodeInfo getParent() {
            return parent;
        }

        @Override
        public boolean isClickable() {
            return clickable;
        }

        @Override
        public boolean performAction(int action) {
            if (action != AccessibilityNodeInfo.ACTION_CLICK || !clickable) {
                return false;
            }
            clickCount++;
            return true;
        }
    }
}
