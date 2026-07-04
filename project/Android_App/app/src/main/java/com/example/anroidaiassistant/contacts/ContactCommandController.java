package com.example.anroidaiassistant.contacts;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class ContactCommandController {
    private final ContactRepository contactRepository;
    private final ContactChoicePresenter contactChoicePresenter;
    private final PhoneDialer phoneDialer;

    public ContactCommandController() {
        this.phoneDialer = new PhoneDialer();
        ContactMatcher contactMatcher = new ContactMatcher();
        this.contactRepository = new ContactRepository(contactMatcher, phoneDialer);
        this.contactChoicePresenter = new ContactChoicePresenter(phoneDialer);
    }

    public void callContact(String contactName, CommandExecutionContext executionContext) {
        if (!hasText(contactName)) {
            executionContext.showMessage("Who should I call?");
            return;
        }

        if (phoneDialer.looksLikePhoneNumber(contactName)) {
            phoneDialer.callPhoneNumber(phoneDialer.normalizePhoneNumber(contactName), executionContext);
            return;
        }

        List<ContactPhoneMatch> contactPhoneMatches = contactRepository.findContactPhoneMatches(
                contactName,
                executionContext
        );
        if (contactPhoneMatches.isEmpty()) {
            executionContext.showMessage("Contact not found or has no phone number: " + contactName);
            return;
        }

        List<ContactPhoneMatch> exactContactMatches = exactContactMatches(contactPhoneMatches);
        if (exactContactMatches.size() == 1) {
            phoneDialer.callPhoneNumber(exactContactMatches.get(0).getPhoneNumber(), executionContext);
            return;
        }

        if (exactContactMatches.size() > 1) {
            showContactChoice(exactContactMatches, contactName, executionContext);
            return;
        }

        showContactChoice(contactPhoneMatches, contactName, executionContext);
    }

    private List<ContactPhoneMatch> exactContactMatches(List<ContactPhoneMatch> contactPhoneMatches) {
        List<ContactPhoneMatch> exactMatches = new ArrayList<>();
        for (ContactPhoneMatch match : contactPhoneMatches) {
            if (match.isExact()) {
                exactMatches.add(match);
            }
        }
        return exactMatches;
    }

    private void showContactChoice(
            List<ContactPhoneMatch> matches,
            String contactName,
            CommandExecutionContext executionContext
    ) {
        contactChoicePresenter.showContactChoice(
                matches,
                contactName,
                executionContext,
                match -> phoneDialer.callPhoneNumber(match.getPhoneNumber(), executionContext)
        );
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}