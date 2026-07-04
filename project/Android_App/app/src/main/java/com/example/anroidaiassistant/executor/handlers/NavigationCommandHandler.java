package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class NavigationCommandHandler implements CommandHandler {
    public enum Action {
        HOME,
        BACK,
        CLOSE_APP,
        RECENTS,
        NOTIFICATIONS,
        SCREENSHOT
    }

    private final String intent;
    private final Action action;

    public NavigationCommandHandler(String intent, Action action) {
        this.intent = intent;
        this.action = action;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            context.showMessage("Accessibility service is not connected");
            return;
        }

        switch (action) {
            case HOME:
                service.performHome();
                break;
            case BACK:
                service.performBack();
                break;
            case CLOSE_APP:
                service.performCloseApp();
                break;
            case RECENTS:
                service.performRecents();
                break;
            case NOTIFICATIONS:
                service.performNotifications();
                break;
            case SCREENSHOT:
                if (!service.performScreenshot()) {
                    context.showMessage("Screenshot is not supported on this device");
                }
                break;
            default:
                context.showMessage("Navigation action not supported");
                break;
        }
    }
}
