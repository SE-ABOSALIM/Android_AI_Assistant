package com.example.anroidaiassistant.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class QuickSettingsTileControllerTest {
    @Test
    public void tileFoundOnFirstSearch_clicksOnceAndSchedulesNoRetry() {
        FakeAccessibilityService service = new FakeAccessibilityService();
        FakeNode tile = new FakeNode("Wi-Fi off").clickable();
        service.rootNode = tile;
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer();
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        FakePageMover pageMover = new FakePageMover();
        QuickSettingsTileController controller = controller(
                service,
                globalActions,
                scheduler,
                pageMover
        );

        assertTrue(controller.setTileState("SET_WIFI", "on", null));
        assertEquals(
                Collections.singletonList(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
                globalActions.actions
        );
        assertEquals(1, scheduler.pendingCount());

        scheduler.runNext();

        assertEquals(1, tile.clickCount);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(Collections.singletonList(450L), scheduler.delays);
        assertEquals(0, pageMover.leftMoves);
        assertEquals(1, globalActions.actions.size());
    }

    @Test
    public void tileAlreadyInDesiredState_doesNotClickOrRetry() {
        FakeAccessibilityService service = new FakeAccessibilityService();
        FakeNode tile = new FakeNode("Wi-Fi on").clickable();
        service.rootNode = tile;
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer();
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        FakePageMover pageMover = new FakePageMover();
        QuickSettingsTileController controller = controller(
                service,
                globalActions,
                scheduler,
                pageMover
        );

        assertTrue(controller.setTileState("SET_WIFI", "on", null));
        scheduler.runNext();

        assertEquals(0, tile.clickCount);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(Collections.singletonList(450L), scheduler.delays);
        assertEquals(0, pageMover.leftMoves);
        assertEquals(1, globalActions.actions.size());
    }

    @Test
    public void searchExhaustion_isBoundedAndTerminates() {
        FakeAccessibilityService service = new FakeAccessibilityService();
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer();
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        FakePageMover pageMover = new FakePageMover();
        QuickSettingsTileController controller = controller(
                service,
                globalActions,
                scheduler,
                pageMover
        );

        assertTrue(controller.setTileState("SET_WIFI", "on", null));
        scheduler.runUntilIdle(10);

        assertEquals(4, service.rootReadCount);
        assertEquals(4, scheduler.executedCount);
        assertEquals(Arrays.asList(450L, 450L, 450L, 450L), scheduler.delays);
        assertEquals(2, pageMover.leftMoves);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    public void bluetoothStateObservedAfterFirstClick_stopsVerification() {
        FakeAccessibilityService service = new FakeAccessibilityService();
        FakeNode observedOffTile = new FakeNode("Bluetooth off");
        FakeNode initialOnTile = new FakeNode("Bluetooth on")
                .clickable()
                .afterClick(() -> service.rootNode = observedOffTile);
        service.rootNode = initialOnTile;
        FakeGlobalActionPerformer globalActions = new FakeGlobalActionPerformer();
        FakeDelayScheduler scheduler = new FakeDelayScheduler();
        FakePageMover pageMover = new FakePageMover();
        QuickSettingsTileController controller = controller(
                service,
                globalActions,
                scheduler,
                pageMover
        );

        assertTrue(controller.setTileState("SET_BLUETOOTH", "off", null));
        scheduler.runNext();
        assertEquals(1, initialOnTile.clickCount);
        assertEquals(1, scheduler.pendingCount());

        scheduler.runNext();

        assertEquals(1, initialOnTile.clickCount);
        assertEquals(0, observedOffTile.clickCount);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(Arrays.asList(450L, 1200L), scheduler.delays);
        assertEquals(0, pageMover.leftMoves);
        assertEquals(1, globalActions.actions.size());
    }

    private static QuickSettingsTileController controller(
            FakeAccessibilityService service,
            FakeGlobalActionPerformer globalActions,
            FakeDelayScheduler scheduler,
            FakePageMover pageMover
    ) {
        return new QuickSettingsTileController(
                service,
                null,
                globalActions,
                scheduler,
                pageMover
        );
    }

    private static final class FakeAccessibilityService extends AccessibilityService {
        private AccessibilityNodeInfo rootNode;
        private int rootReadCount;

        @Override
        public AccessibilityNodeInfo getRootInActiveWindow() {
            rootReadCount++;
            return rootNode;
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
        }

        @Override
        public void onInterrupt() {
        }
    }

    private static final class FakeGlobalActionPerformer
            implements QuickSettingsTileController.GlobalActionPerformer {
        private final List<Integer> actions = new ArrayList<>();

        @Override
        public boolean perform(int action) {
            actions.add(action);
            return true;
        }
    }

    private static final class FakeDelayScheduler
            implements QuickSettingsTileController.DelayScheduler {
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
            assertFalse("Expected a scheduled task", pendingTasks.isEmpty());
            executedCount++;
            pendingTasks.removeFirst().run();
        }

        private void runUntilIdle(int maximumTasks) {
            int remaining = maximumTasks;
            while (!pendingTasks.isEmpty() && remaining > 0) {
                runNext();
                remaining--;
            }
            assertTrue("Scheduled work did not terminate", pendingTasks.isEmpty());
        }
    }

    private static final class FakePageMover implements QuickSettingsTileController.PageMover {
        private int leftMoves;

        @Override
        public void moveLeft() {
            leftMoves++;
        }
    }

    private static final class FakeNode extends AccessibilityNodeInfo {
        private final CharSequence text;
        private boolean clickable;
        private int clickCount;
        private Runnable afterClick;

        private FakeNode(CharSequence text) {
            this.text = text;
        }

        private FakeNode clickable() {
            clickable = true;
            return this;
        }

        private FakeNode afterClick(Runnable action) {
            afterClick = action;
            return this;
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
        public int getChildCount() {
            return 0;
        }

        @Override
        public AccessibilityNodeInfo getChild(int index) {
            return null;
        }

        @Override
        public AccessibilityNodeInfo getParent() {
            return null;
        }

        @Override
        public boolean isCheckable() {
            return false;
        }

        @Override
        public boolean isClickable() {
            return clickable;
        }

        @Override
        public int getActions() {
            return clickable ? AccessibilityNodeInfo.ACTION_CLICK : 0;
        }

        @Override
        public boolean performAction(int action) {
            if (action != AccessibilityNodeInfo.ACTION_CLICK || !clickable) {
                return false;
            }
            clickCount++;
            if (afterClick != null) {
                afterClick.run();
            }
            return true;
        }
    }
}
