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

    @Test
    public void defaultRegistryDispatchesScreenshotIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "TAKE_SCREENSHOT",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Accessibility service is not connected"), messages);
    }

    @Test
    public void defaultRegistryDispatchesPhotoIntentWithCameraParameter() {
        for (String camera : new String[]{"front", "back"}) {
            List<String> messages = new ArrayList<>();
            CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

            boolean dispatched = dispatcher.dispatch(
                    "TAKE_PHOTO",
                    Collections.singletonMap("camera", camera),
                    new CommandExecutionContext(null, messages::add)
            );

            assertTrue(dispatched);
            assertEquals(Collections.singletonList("Camera unavailable"), messages);
        }
    }

    @Test
    public void defaultRegistryDispatchesBrightnessIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "ADJUST_BRIGHTNESS",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Should I increase or decrease brightness?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesSystemSettingIntents() {
        for (String intent : new String[]{
                "SET_WIFI",
                "SET_BLUETOOTH",
                "SET_FLASHLIGHT",
                "SET_LOCATION",
                "SET_MOBILE_DATA",
                "SET_MOBILE_HOTSPOT"
        }) {
            List<String> messages = new ArrayList<>();
            CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

            boolean dispatched = dispatcher.dispatch(
                    intent,
                    Collections.emptyMap(),
                    new CommandExecutionContext(null, messages::add)
            );

            assertTrue(dispatched);
            assertEquals(Collections.singletonList("Should I turn it on or off?"), messages);
        }
    }

    @Test
    public void quickSettingsIntentsRequireAccessibilityServiceWhenStateIsProvided() {
        for (String intent : new String[]{
                "SET_WIFI",
                "SET_BLUETOOTH",
                "SET_LOCATION",
                "SET_MOBILE_DATA",
                "SET_MOBILE_HOTSPOT"
        }) {
            List<String> messages = new ArrayList<>();
            CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

            boolean dispatched = dispatcher.dispatch(
                    intent,
                    Collections.singletonMap("state", "on"),
                    new CommandExecutionContext(null, messages::add)
            );

            assertTrue(dispatched);
            assertEquals(Collections.singletonList("Accessibility service is not connected"), messages);
        }
    }

    @Test
    public void defaultRegistryDispatchesKeyboardIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "SET_KEYBOARD",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Should I open or close the keyboard?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesSoundModeIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "SET_SOUND_MODE",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Which sound mode should I set?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesAppManagementIntents() {
        List<String> infoMessages = new ArrayList<>();
        CommandDispatcher infoDispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean infoDispatched = infoDispatcher.dispatch(
                "OPEN_APP_INFO",
                Collections.emptyMap(),
                new CommandExecutionContext(null, infoMessages::add)
        );

        assertTrue(infoDispatched);
        assertEquals(Collections.singletonList("Which app info should I open?"), infoMessages);

        List<String> uninstallMessages = new ArrayList<>();
        CommandDispatcher uninstallDispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean uninstallDispatched = uninstallDispatcher.dispatch(
                "UNINSTALL_APP",
                Collections.emptyMap(),
                new CommandExecutionContext(null, uninstallMessages::add)
        );

        assertTrue(uninstallDispatched);
        assertEquals(Collections.singletonList("Which app should I uninstall?"), uninstallMessages);
    }

    @Test
    public void defaultRegistryDispatchesAlarmIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "SET_ALARM",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("What time should I set the alarm for?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesSearchIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "SEARCH_QUERY",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("What should I search for?"), messages);
    }

    @Test
    public void defaultRegistryDispatchesCenterGestureIntents() {
        for (String intent : new String[]{"DOUBLE_TAP", "HOLD_SCREEN"}) {
            List<String> messages = new ArrayList<>();
            CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

            boolean dispatched = dispatcher.dispatch(
                    intent,
                    Collections.emptyMap(),
                    new CommandExecutionContext(null, messages::add)
            );

            assertTrue(dispatched);
            assertEquals(Collections.singletonList("Accessibility service is not connected"), messages);
        }
    }

    @Test
    public void defaultRegistryDispatchesClearTextIntent() {
        List<String> messages = new ArrayList<>();
        CommandDispatcher dispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean dispatched = dispatcher.dispatch(
                "CLEAR_TEXT",
                Collections.emptyMap(),
                new CommandExecutionContext(null, messages::add)
        );

        assertTrue(dispatched);
        assertEquals(Collections.singletonList("Accessibility service is not connected"), messages);
    }

    @Test
    public void defaultRegistryDispatchesWriteTextIntent() {
        List<String> missingMessages = new ArrayList<>();
        CommandDispatcher missingDispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean missingDispatched = missingDispatcher.dispatch(
                "WRITE_TEXT",
                Collections.emptyMap(),
                new CommandExecutionContext(null, missingMessages::add)
        );

        assertTrue(missingDispatched);
        assertEquals(Collections.singletonList("What should I write?"), missingMessages);

        List<String> serviceMessages = new ArrayList<>();
        CommandDispatcher serviceDispatcher = CommandHandlerRegistry.createDefaultDispatcher(new AppOpenController());

        boolean serviceDispatched = serviceDispatcher.dispatch(
                "WRITE_TEXT",
                Collections.singletonMap("text", "hello"),
                new CommandExecutionContext(null, serviceMessages::add)
        );

        assertTrue(serviceDispatched);
        assertEquals(Collections.singletonList("Accessibility service is not connected"), serviceMessages);
    }
}
