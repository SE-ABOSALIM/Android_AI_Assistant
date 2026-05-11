package com.example.anroidaiassistant.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}