package com.example.anroidaiassistant.executor;

import com.example.anroidaiassistant.accessibility.DevicePowerController;
import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.handlers.AlarmCommandHandler;
import com.example.anroidaiassistant.executor.handlers.AppCommandHandler;
import com.example.anroidaiassistant.executor.handlers.BrightnessCommandHandler;
import com.example.anroidaiassistant.executor.handlers.CallControlCommandHandler;
import com.example.anroidaiassistant.executor.handlers.CameraCommandHandler;
import com.example.anroidaiassistant.executor.handlers.CenterGestureCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ClearTextCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ClickItemCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ContactCommandHandler;
import com.example.anroidaiassistant.executor.handlers.GridCommandHandler;
import com.example.anroidaiassistant.executor.handlers.LabelsCommandHandler;
import com.example.anroidaiassistant.executor.handlers.NavigationCommandHandler;
import com.example.anroidaiassistant.executor.handlers.OpenAppCommandHandler;
import com.example.anroidaiassistant.executor.handlers.PowerCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ScrollCommandHandler;
import com.example.anroidaiassistant.executor.handlers.SearchCommandHandler;
import com.example.anroidaiassistant.executor.handlers.StopListeningCommandHandler;
import com.example.anroidaiassistant.executor.handlers.SystemSettingCommandHandler;
import com.example.anroidaiassistant.executor.handlers.SwipeCommandHandler;
import com.example.anroidaiassistant.executor.handlers.TimerCommandHandler;
import com.example.anroidaiassistant.executor.handlers.VolumeCommandHandler;
import com.example.anroidaiassistant.executor.handlers.WriteTextCommandHandler;

import java.util.Arrays;

public final class CommandHandlerRegistry {
    private CommandHandlerRegistry() {}

    public static CommandDispatcher createDefaultDispatcher(AppOpenController appOpenController) {
        return new CommandDispatcher(Arrays.asList(
                new OpenAppCommandHandler(appOpenController),
                new CallControlCommandHandler(
                        "ANSWER_CALL",
                        CallControlCommandHandler.Action.ANSWER
                ),
                new CallControlCommandHandler(
                        "REJECT_CALL",
                        CallControlCommandHandler.Action.REJECT
                ),
                new AppCommandHandler(
                        "OPEN_APP_INFO",
                        AppOpenController.AppAction.OPEN_INFO,
                        appOpenController
                ),
                new AppCommandHandler(
                        "UNINSTALL_APP",
                        AppOpenController.AppAction.UNINSTALL,
                        appOpenController
                ),
                new VolumeCommandHandler(),
                new BrightnessCommandHandler(),
                new ScrollCommandHandler(),
                new SwipeCommandHandler(),
                new CenterGestureCommandHandler(
                        "DOUBLE_TAP",
                        CenterGestureCommandHandler.Action.DOUBLE_TAP
                ),
                new CenterGestureCommandHandler(
                        "HOLD_SCREEN",
                        CenterGestureCommandHandler.Action.LONG_PRESS
                ),
                new NavigationCommandHandler("GO_HOME", NavigationCommandHandler.Action.HOME),
                new NavigationCommandHandler("GO_BACK", NavigationCommandHandler.Action.BACK),
                new NavigationCommandHandler("CLOSE_APP", NavigationCommandHandler.Action.CLOSE_APP),
                new NavigationCommandHandler("SHOW_RECENTS", NavigationCommandHandler.Action.RECENTS),
                new NavigationCommandHandler("OPEN_NOTIFICATIONS", NavigationCommandHandler.Action.NOTIFICATIONS),
                new NavigationCommandHandler("TAKE_SCREENSHOT", NavigationCommandHandler.Action.SCREENSHOT),
                new PowerCommandHandler("POWER_OFF", DevicePowerController.Action.POWER_OFF),
                new PowerCommandHandler("RESTART_DEVICE", DevicePowerController.Action.RESTART),
                new GridCommandHandler(),
                new LabelsCommandHandler(),
                new TimerCommandHandler(),
                new AlarmCommandHandler(),
                new CameraCommandHandler(),
                new ContactCommandHandler(),
                new SearchCommandHandler(),
                new WriteTextCommandHandler(),
                new ClearTextCommandHandler(),
                new ClickItemCommandHandler(),
                new SystemSettingCommandHandler("SET_WIFI"),
                new SystemSettingCommandHandler("SET_BLUETOOTH"),
                new SystemSettingCommandHandler("SET_FLASHLIGHT"),
                new SystemSettingCommandHandler("SET_LOCATION"),
                new SystemSettingCommandHandler("SET_MOBILE_DATA"),
                new SystemSettingCommandHandler("SET_MOBILE_HOTSPOT"),
                new SystemSettingCommandHandler("SET_SOUND_MODE"),
                new SystemSettingCommandHandler("SET_KEYBOARD"),
                new StopListeningCommandHandler()
        ));
    }
}
