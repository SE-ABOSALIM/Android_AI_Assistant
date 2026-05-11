package com.example.anroidaiassistant.contacts;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.executor.CommandExecutionContext;

import java.util.ArrayList;
import java.util.List;

public final class ContactChoicePresenter {
    public interface SelectionCallback {
        void onSelected(ContactPhoneMatch match);
    }

    private final PhoneDialer phoneDialer;

    public ContactChoicePresenter(PhoneDialer phoneDialer) {
        this.phoneDialer = phoneDialer;
    }

    public void showContactChoice(
            List<ContactPhoneMatch> matches,
            String contactName,
            CommandExecutionContext executionContext,
            SelectionCallback callback
    ) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            executionContext.showMessage("Multiple contacts match. Start listening and say the number.");
            return;
        }

        List<MyAccessibilityService.NumberedChoice> choices = new ArrayList<>();
        for (ContactPhoneMatch match : matches) {
            choices.add(new MyAccessibilityService.NumberedChoice(
                    match.getDisplayName(),
                    phoneDialer.formatPhoneNumberForDisplay(match.getPhoneNumber())
            ));
        }

        service.startNumberSelection(
                buildContactChoiceTitle(matches, contactName),
                choices,
                new MyAccessibilityService.NumberSelectionCallback() {
                    @Override
                    public void onSelected(int selectedIndex) {
                        callback.onSelected(matches.get(selectedIndex));
                    }

                    @Override
                    public void onCancelled() {
                        executionContext.showMessage("Contact selection cancelled");
                    }
                }
        );
    }

    private String buildContactChoiceTitle(List<ContactPhoneMatch> matches, String contactName) {
        if (matches.size() == 1 && !matches.get(0).isExact()) {
            return "Benzer kisi bulundu: " + contactName;
        }
        return "Birden fazla kisi bulundu: " + contactName;
    }
}