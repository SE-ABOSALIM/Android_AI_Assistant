package com.example.anroidaiassistant.api.auth;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class InstallationAuthInterceptor implements Interceptor {
    private final InstallationAuthCoordinator coordinator;

    public InstallationAuthInterceptor(InstallationAuthCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String credential = coordinator.getOrRegisterCredential();
        Request originalRequest = chain.request();
        Request authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + credential)
                .build();
        Response response = chain.proceed(authenticatedRequest);
        if (response.code() != 401 || !isReplayable(originalRequest)) {
            return response;
        }

        if (response.body() != null) {
            response.body().close();
        }
        String replacementCredential = coordinator.recoverCredentialAfterUnauthorized(credential);
        Request retryRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + replacementCredential)
                .build();
        return chain.proceed(retryRequest);
    }

    private static boolean isReplayable(Request request) {
        RequestBody body = request.body();
        return body == null || (!body.isOneShot() && !body.isDuplex());
    }
}
