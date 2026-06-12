package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.accessibility.DevicePowerController;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PowerCommandHandler implements CommandHandler {
    private final String intent;
    private final DevicePowerController.Action action;

    public PowerCommandHandler(String intent, DevicePowerController.Action action) {
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
        if (service == null || !service.isContinuousListeningActive()) {
            context.showMessage("Start listening to confirm this action");
            return;
        }

        String language = service.getSelectedLanguage();
        List<MyAccessibilityService.NumberedChoice> choices = new ArrayList<>();
        choices.add(new MyAccessibilityService.NumberedChoice(yesText(language), ""));
        choices.add(new MyAccessibilityService.NumberedChoice(noText(language), ""));

        service.startConfirmationSelection(
                confirmationTitle(language),
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        if (selectedIndex != 0) {
                            context.showMessage(cancelledText(language));
                            return;
                        }

                        if (!service.performDevicePowerAction(action)) {
                            context.showMessage("Power menu is not supported on this device");
                        }
                    }

                    @Override
                    public void onCancelled() {
                        context.showMessage(cancelledText(language));
                    }
                }
        );
    }

    private String confirmationTitle(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return action == DevicePowerController.Action.RESTART
                    ? "Restart the phone?"
                    : "Power off the phone?";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return action == DevicePowerController.Action.RESTART
                    ? "\u0647\u0644 \u062a\u0631\u064a\u062f \u0627\u0639\u0627\u062f\u0629 \u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u0647\u0627\u062a\u0641\u061f"
                    : "\u0647\u0644 \u062a\u0631\u064a\u062f \u0627\u064a\u0642\u0627\u0641 \u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u0647\u0627\u062a\u0641\u061f";
        }
        return action == DevicePowerController.Action.RESTART
                ? "Telefonu yeniden baslatmak istiyor musunuz?"
                : "Telefonu kapatmak istiyor musunuz?";
    }

    private String yesText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "Yes";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0646\u0639\u0645";
        }
        return "Evet";
    }

    private String noText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "No";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u0644\u0627";
        }
        return "Hayir";
    }

    private String cancelledText(String language) {
        if ("EN".equalsIgnoreCase(language)) {
            return "Cancelled";
        }
        if ("AR".equalsIgnoreCase(language)) {
            return "\u062a\u0645 \u0627\u0644\u0627\u0644\u063a\u0627\u0621";
        }
        return "Iptal edildi";
    }
}
