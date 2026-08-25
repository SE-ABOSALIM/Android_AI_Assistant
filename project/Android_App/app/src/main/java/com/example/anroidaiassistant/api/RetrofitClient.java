package com.example.anroidaiassistant.api;

import android.content.Context;

import com.example.anroidaiassistant.BuildConfig;
import com.example.anroidaiassistant.api.auth.InstallationAuthCoordinator;
import com.example.anroidaiassistant.api.auth.InstallationAuthInterceptor;
import com.example.anroidaiassistant.api.auth.InstallationCredentialStore;
import com.example.anroidaiassistant.api.auth.InstallationRegistrationRequestFactory;
import com.example.anroidaiassistant.api.dto.InstallationRegistrationRequest;
import com.example.anroidaiassistant.api.dto.InstallationRegistrationResponse;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.util.DeviceIdentity;

import java.io.IOException;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit;
    private static Context applicationContext;

    public static synchronized void initialize(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Application context is required");
        }
        if (applicationContext == null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public static synchronized Retrofit getClient() {
        if (applicationContext == null) {
            throw new IllegalStateException("RetrofitClient must be initialized by the application");
        }
        if (retrofit == null) {
            Retrofit registrationRetrofit = baseBuilder().build();
            ApiService registrationApi = registrationRetrofit.create(ApiService.class);
            InstallationCredentialStore credentialStore = new InstallationCredentialStore(applicationContext);
            InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                    credentialStore,
                    () -> registerInstallation(registrationApi)
            );
            OkHttpClient authenticatedClient = new OkHttpClient.Builder()
                    .addInterceptor(new InstallationAuthInterceptor(coordinator))
                    .build();
            retrofit = baseBuilder()
                    .client(authenticatedClient)
                    .build();
        }
        return retrofit;
    }

    private static Retrofit.Builder baseBuilder() {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create());
    }

    private static String registerInstallation(ApiService registrationApi) throws IOException {
        String deviceId = DeviceIdentity.getDeviceId(applicationContext);
        InstallationRegistrationRequest request = InstallationRegistrationRequestFactory.create(
                deviceId,
                BuildConfig.VERSION_NAME,
                AssistantSettings.getLanguage(applicationContext)
        );
        Response<InstallationRegistrationResponse> response = registrationApi
                .registerInstallation(request)
                .execute();
        InstallationRegistrationResponse body = response.body();
        if (!response.isSuccessful()
                || body == null
                || body.getCredential() == null
                || body.getCredential().trim().isEmpty()) {
            throw new IOException("Installation registration failed with HTTP " + response.code());
        }
        return body.getCredential().trim();
    }
}
