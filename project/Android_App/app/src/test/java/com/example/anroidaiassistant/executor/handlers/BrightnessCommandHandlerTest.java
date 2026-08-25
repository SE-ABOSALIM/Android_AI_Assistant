package com.example.anroidaiassistant.executor.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.provider.Settings;

import com.example.anroidaiassistant.executor.CommandExecutionContext;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrightnessCommandHandlerTest {
    @Test
    public void manualMode_increaseWritesCurrentPlus45Within255() {
        FakeSystemSettingsAccess settingsAccess = new FakeSystemSettingsAccess();
        settingsAccess.values.put(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        );
        settingsAccess.values.put(Settings.System.SCREEN_BRIGHTNESS, 230);
        List<String> messages = new ArrayList<>();
        BrightnessCommandHandler handler = new BrightnessCommandHandler(settingsAccess);

        handler.handle(
                Collections.singletonMap("brightness", "increase"),
                executionContext(messages)
        );

        assertEquals(Integer.valueOf(255), settingsAccess.values.get(Settings.System.SCREEN_BRIGHTNESS));
        assertEquals(Arrays.asList(
                Settings.System.SCREEN_BRIGHTNESS_MODE + "="
                        + Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                Settings.System.SCREEN_BRIGHTNESS + "=255"
        ), settingsAccess.writes);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void manualMode_decreaseWritesCurrentMinus45Within10() {
        FakeSystemSettingsAccess settingsAccess = new FakeSystemSettingsAccess();
        settingsAccess.values.put(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        );
        settingsAccess.values.put(Settings.System.SCREEN_BRIGHTNESS, 30);
        List<String> messages = new ArrayList<>();
        BrightnessCommandHandler handler = new BrightnessCommandHandler(settingsAccess);

        handler.handle(
                Collections.singletonMap("brightness", "decrease"),
                executionContext(messages)
        );

        assertEquals(Integer.valueOf(10), settingsAccess.values.get(Settings.System.SCREEN_BRIGHTNESS));
        assertEquals(Arrays.asList(
                Settings.System.SCREEN_BRIGHTNESS_MODE + "="
                        + Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                Settings.System.SCREEN_BRIGHTNESS + "=10"
        ), settingsAccess.writes);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void missingWritePermission_doesNotModifyBrightness() {
        FakeSystemSettingsAccess settingsAccess = new FakeSystemSettingsAccess();
        settingsAccess.canWrite = false;
        settingsAccess.values.put(Settings.System.SCREEN_BRIGHTNESS, 120);
        List<String> messages = new ArrayList<>();
        BrightnessCommandHandler handler = new BrightnessCommandHandler(settingsAccess);

        handler.handle(
                Collections.singletonMap("brightness", "increase"),
                executionContext(messages)
        );

        assertEquals(Integer.valueOf(120), settingsAccess.values.get(Settings.System.SCREEN_BRIGHTNESS));
        assertTrue(settingsAccess.writes.isEmpty());
        assertEquals(
                Collections.singletonList("System settings access is required for brightness control"),
                messages
        );
    }

    private static CommandExecutionContext executionContext(List<String> messages) {
        return new CommandExecutionContext(new ContextWrapper(null), messages::add);
    }

    private static final class FakeSystemSettingsAccess implements SystemSettingsAccess {
        private final Map<String, Integer> values = new HashMap<>();
        private final List<String> writes = new ArrayList<>();
        private boolean canWrite = true;

        @Override
        public boolean canWrite(Context context) {
            return canWrite;
        }

        @Override
        public int getInt(Context context, String name, int defaultValue) {
            Integer value = values.get(name);
            return value == null ? defaultValue : value;
        }

        @Override
        public void putInt(Context context, String name, int value) {
            values.put(name, value);
            writes.add(name + "=" + value);
        }
    }
}
