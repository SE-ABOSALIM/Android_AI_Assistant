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

public class InputFocusCommandHandlerTest {
    @Test
    public void genericFocusUsesExistingGenericSelectionPath() {
        FakeInputFocusAccess access = new FakeInputFocusAccess();

        handler(access).handle(focusParameters(null), context(new ArrayList<>()));

        assertEquals(1, access.genericFocusCalls);
        assertEquals(0, access.namedFocusCalls);
    }

    @Test
    public void namedFocusUsesOnlyNamedSelectionPath() {
        FakeInputFocusAccess access = new FakeInputFocusAccess();

        handler(access).handle(focusParameters("day"), context(new ArrayList<>()));

        assertEquals(0, access.genericFocusCalls);
        assertEquals(1, access.namedFocusCalls);
        assertEquals("day", access.lastTarget);
    }

    @Test
    public void missingNamedTargetNeverFallsBackToGenericSelection() {
        FakeInputFocusAccess access = new FakeInputFocusAccess();
        access.namedHandled = false;
        List<String> messages = new ArrayList<>();

        handler(access).handle(focusParameters("qwertyzxcv"), context(messages));

        assertEquals(0, access.genericFocusCalls);
        assertEquals(1, access.namedFocusCalls);
        assertTrue(messages.contains("Text field not found"));
    }

    @Test
    public void unfocusRemainsGenericAndIgnoresTargetText() {
        FakeInputFocusAccess access = new FakeInputFocusAccess();
        Map<String, Object> parameters = focusParameters("day");
        parameters.put("focus_action", "unfocus");

        handler(access).handle(parameters, context(new ArrayList<>()));

        assertEquals(1, access.unfocusCalls);
        assertEquals(0, access.namedFocusCalls);
        assertEquals(0, access.genericFocusCalls);
    }

    private InputFocusCommandHandler handler(FakeInputFocusAccess access) {
        return new InputFocusCommandHandler(() -> access);
    }

    private Map<String, Object> focusParameters(String targetText) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("focus_action", "focus");
        if (targetText != null) {
            parameters.put("target_text", targetText);
        }
        return parameters;
    }

    private CommandExecutionContext context(List<String> messages) {
        return new CommandExecutionContext(null, messages::add);
    }

    private static final class FakeInputFocusAccess implements InputFocusCommandHandler.InputFocusAccess {
        int genericFocusCalls;
        int namedFocusCalls;
        int unfocusCalls;
        String lastTarget;
        boolean namedHandled = true;

        @Override
        public boolean focusInputField() {
            genericFocusCalls++;
            return true;
        }

        @Override
        public boolean focusInputField(String targetText) {
            namedFocusCalls++;
            lastTarget = targetText;
            return namedHandled;
        }

        @Override
        public boolean unfocusInputField() {
            unfocusCalls++;
            return true;
        }
    }
}
