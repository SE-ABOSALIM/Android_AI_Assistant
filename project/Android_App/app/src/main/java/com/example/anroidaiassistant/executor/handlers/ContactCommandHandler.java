package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.contacts.ContactCommandController;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.util.ParameterReader;

import java.util.Map;

public final class ContactCommandHandler implements CommandHandler {
    private final ContactCommandController contactCommandController;

    public ContactCommandHandler() {
        this(new ContactCommandController());
    }

    ContactCommandHandler(ContactCommandController contactCommandController) {
        this.contactCommandController = contactCommandController;
    }

    @Override
    public String getIntent() {
        return "CALL_CONTACT";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        contactCommandController.callContact(
                ParameterReader.getStringParam(parameters, "contact_name"),
                context
        );
    }
}