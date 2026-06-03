package com.example.anroidaiassistant.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.anroidaiassistant.apps.AppOpenController;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandDispatcherTest {
    @Test
    public void dispatchesRegisteredIntentCaseInsensitively() {
        List<String> calls = new ArrayList<>();
        CommandHandler handler = new CommandHandler() {
            @Override
            public String getIntent() {
                return "TEST_INTENT";
            }

            @Override
            public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
                calls.add("handled");
            }
        };

        CommandDispatcher dispatcher = new CommandDispatcher(Collections.singletonList(handler));

        boolean dispatched = dispatcher.dispatch(
                "test_intent",
                Collections.emptyMap(),
                new CommandExecutionContext(null, null)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("handled"), calls);
    }

    @Test
    public void returnsFalseForUnknownIntent() {
        CommandDispatcher dispatcher = new CommandDispatcher(Collections.emptyList());

        assertFalse(dispatcher.dispatch(
                "UNKNOWN_TEST_INTENT",
                Collections.emptyMap(),
                new CommandExecutionContext(null, null)
        ));
    }
    @Test
    public void defaultRegistryDispatchesContactIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "CALL_CONTACT",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Who should I call?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesScrollIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "SCROLL_SCREEN",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Which direction?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesCloseAppIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "CLOSE_APP",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Accessibility service is not connected"), messages);
    }
}
