package com.example.anroidaiassistant.executor;

import java.util.Map;

public interface CommandHandler {
    String getIntent();

    void handle(Map<String, Object> parameters, CommandExecutionContext context);
}