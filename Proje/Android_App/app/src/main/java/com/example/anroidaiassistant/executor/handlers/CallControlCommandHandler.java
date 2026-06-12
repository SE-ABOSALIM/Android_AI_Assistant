package com.example.anroidaiassistant.executor.handlers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.TelecomManager;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.executor.CommandHandler;

import java.util.Map;

public final class CallControlCommandHandler implements CommandHandler {
    public enum Action {
        ANSWER,
        REJECT
    }

    private final String intent;
    private final Action action;

    public CallControlCommandHandler(String intent, Action action) {
        this.intent = intent;
        this.action = action;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    @Override
    public void handle(Map<String, Object> parameters, CommandExecutionContext context) {
        Context androidContext = context.getAndroidContext();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            context.showMessage("Call control is not supported on this device");
            return;
        }

        if (androidContext.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED) {
            context.showMessage("Answer phone calls permission is required");
            return;
        }

        TelecomManager telecomManager =
                (TelecomManager) androidContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager == null) {
            context.showMessage("Call service is unavailable");
            return;
        }

        try {
            if (action == Action.ANSWER) {
                telecomManager.acceptRingingCall();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager.endCall()) {
                return;
            }

            context.showMessage("Could not reject the call");
        } catch (SecurityException exception) {
            context.showMessage("Call control permission is unavailable");
        } catch (Exception exception) {
            context.showMessage("Could not control the call");
        }
    }
}
