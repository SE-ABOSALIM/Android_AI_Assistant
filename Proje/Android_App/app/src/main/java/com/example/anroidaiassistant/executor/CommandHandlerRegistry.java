package com.example.anroidaiassistant.executor;

import com.example.anroidaiassistant.apps.AppOpenController;
import com.example.anroidaiassistant.executor.handlers.CameraCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ContactCommandHandler;
import com.example.anroidaiassistant.executor.handlers.NavigationCommandHandler;
import com.example.anroidaiassistant.executor.handlers.OpenAppCommandHandler;
import com.example.anroidaiassistant.executor.handlers.ScrollCommandHandler;
import com.example.anroidaiassistant.executor.handlers.StopListeningCommandHandler;
import com.example.anroidaiassistant.executor.handlers.SwipeCommandHandler;
import com.example.anroidaiassistant.executor.handlers.TimerCommandHandler;
import com.example.anroidaiassistant.executor.handlers.VolumeCommandHandler;

import java.util.Arrays;

public final class CommandHandlerRegistry {
    private CommandHandlerRegistry() {}

    public static CommandDispatcher createDefaultDispatcher(AppOpenController appOpenController) {
        return new CommandDispatcher(Arrays.asList(
                new OpenAppCommandHandler(appOpenController),
                new VolumeCommandHandler(),
                new ScrollCommandHandler(),
                new SwipeCommandHandler(),
                new NavigationCommandHandler("GO_HOME", NavigationCommandHandler.Action.HOME),
                new NavigationCommandHandler("GO_BACK", NavigationCommandHandler.Action.BACK),
                new NavigationCommandHandler("SHOW_RECENTS", NavigationCommandHandler.Action.RECENTS),
                new NavigationCommandHandler("OPEN_NOTIFICATIONS", NavigationCommandHandler.Action.NOTIFICATIONS),
                new TimerCommandHandler(),
                new CameraCommandHandler(),
                new ContactCommandHandler(),
                new StopListeningCommandHandler()
        ));
    }
}