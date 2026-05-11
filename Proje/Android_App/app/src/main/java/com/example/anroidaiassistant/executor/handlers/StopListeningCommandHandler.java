package com.example.anroidaiassistant.executor.handlers;

import com.example.anroidaiassistant.AppCatalogSyncer;
import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;
import com.example.anroidaiassistant.session.AssistantSession;

import java.util.Map;

public final class StopListeningCommandHandler implements CommandHandler {
    @Override
    public String getIntent() {
        return "STOP_LISTENING";
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            service.stopContinuousListening();
        } else {
            String endedSessionId = AssistantSession.endSession();
            ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
            AppCatalogSyncer.closeSession(apiService, endedSessionId);
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.syncListeningUiState();
        }
    }
}