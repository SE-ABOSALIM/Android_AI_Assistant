package com.example.anroidaiassistant.api.auth;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class InstallationAuthInterceptor implements Interceptor {
    private final InstallationAuthCoordinator coordinator;

    public InstallationAuthInterceptor(InstallationAuthCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String credential = coordinator.getOrRegisterCredential();
        Request authenticatedRequest = chain.request().newBuilder()
                .header("Authorization", "Bearer " + credential)
                .build();
        return chain.proceed(authenticatedRequest);
    }
}
