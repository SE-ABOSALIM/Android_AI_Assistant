package com.example.anroidaiassistant;

import android.app.Application;

import com.example.anroidaiassistant.api.RetrofitClient;

public final class AssistantApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetrofitClient.initialize(this);
    }
}
