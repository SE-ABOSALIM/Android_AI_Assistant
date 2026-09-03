package com.example.anroidaiassistant.executor.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.anroidaiassistant.executor.CommandExecutionContext;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CenterGestureCommandHandlerTest {
    @Test
    public void holdWithoutTargetUsesExistingCenterGesture() {
        FakeGestureAccess access = new FakeGestureAccess();
        CenterGestureCommandHandler handler = handler(
                "HOLD_SCREEN",
                CenterGestureCommandHandler.Action.LONG_PRESS,
                access
        );

        handler.handle(Collections.emptyMap(), context(new ArrayList<>()));

        assertEquals(1, access.longPressCenterCalls);
        assertEquals(0, access.longPressTargetCalls);
    }

    @Test
    public void doubleTapWithoutTargetUsesExistingCenterGesture() {
        FakeGestureAccess access = new FakeGestureAccess();
        CenterGestureCommandHandler handler = handler(
                "DOUBLE_TAP",
                CenterGestureCommandHandler.Action.DOUBLE_TAP,
                access
        );

        handler.handle(Collections.emptyMap(), context(new ArrayList<>()));

        assertEquals(1, access.doubleTapCenterCalls);
        assertEquals(0, access.doubleTapTargetCalls);
    }

    @Test
    public void targetedHoldUsesTargetResolverPathInsteadOfCenter() {
        FakeGestureAccess access = new FakeGestureAccess();
        CenterGestureCommandHandler handler = handler(
                "HOLD_SCREEN",
                CenterGestureCommandHandler.Action.LONG_PRESS,
                access
        );

        handler.handle(targetParameters("notification", "top"), context(new ArrayList<>()));

        assertEquals(0, access.longPressCenterCalls);
        assertEquals(1, access.longPressTargetCalls);
        assertEquals("notification", access.lastTargetText);
        assertEquals("top", access.lastPosition);
    }

    @Test
    public void targetedDoubleTapUsesTargetResolverPathInsteadOfCenter() {
        FakeGestureAccess access = new FakeGestureAccess();
        CenterGestureCommandHandler handler = handler(
                "DOUBLE_TAP",
                CenterGestureCommandHandler.Action.DOUBLE_TAP,
                access
        );

        handler.handle(targetParameters("heart icon", null), context(new ArrayList<>()));

        assertEquals(0, access.doubleTapCenterCalls);
        assertEquals(1, access.doubleTapTargetCalls);
        assertEquals("heart icon", access.lastTargetText);
    }

    @Test
    public void unresolvedExplicitTargetNeverFallsBackToCenter() {
        FakeGestureAccess access = new FakeGestureAccess();
        access.targetHandled = false;
        List<String> messages = new ArrayList<>();
        CenterGestureCommandHandler handler = handler(
                "HOLD_SCREEN",
                CenterGestureCommandHandler.Action.LONG_PRESS,
                access
        );

        handler.handle(targetParameters("nonexistent thing", null), context(messages));

        assertEquals(0, access.longPressCenterCalls);
        assertEquals(1, access.longPressTargetCalls);
        assertTrue(messages.contains("Item not found"));
    }

    private CenterGestureCommandHandler handler(
            String intent,
            CenterGestureCommandHandler.Action action,
            FakeGestureAccess access
    ) {
        return new CenterGestureCommandHandler(intent, action, () -> access);
    }

    private CommandExecutionContext context(List<String> messages) {
        return new CommandExecutionContext(null, messages::add);
    }

    private Map<String, Object> targetParameters(String targetText, String position) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("target_text", targetText);
        if (position != null) {
            parameters.put("position", position);
        }
        return parameters;
    }

    private static final class FakeGestureAccess implements CenterGestureCommandHandler.GestureAccess {
        private int longPressCenterCalls;
        private int doubleTapCenterCalls;
        private int longPressTargetCalls;
        private int doubleTapTargetCalls;
        private String lastTargetText;
        private String lastPosition;
        private boolean targetHandled = true;

        @Override
        public boolean longPressCenter() {
            longPressCenterCalls++;
            return true;
        }

        @Override
        public boolean doubleTapCenter() {
            doubleTapCenterCalls++;
            return true;
        }

        @Override
        public boolean longPressTarget(String targetText, String position) {
            longPressTargetCalls++;
            lastTargetText = targetText;
            lastPosition = position;
            return targetHandled;
        }

        @Override
        public boolean doubleTapTarget(String targetText, String position) {
            doubleTapTargetCalls++;
            lastTargetText = targetText;
            lastPosition = position;
            return targetHandled;
        }
    }
}
