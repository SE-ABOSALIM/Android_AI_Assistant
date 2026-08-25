package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class InstallationAuthInterceptorTest {
    @Test
    public void networking_attachesAuthorizationAutomaticallyAfterRegistration() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    return "issued-credential";
                }
        );
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(coordinator);
        RecordingChain chain = new RecordingChain(200);

        interceptor.intercept(chain);

        assertEquals(1, registrations.get());
        assertEquals("Bearer issued-credential", chain.proceededRequest.header("Authorization"));
        assertEquals("issued-credential", store.getCredential());
    }

    @Test
    public void authError_doesNotTriggerRegistrationLoop() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthCoordinator coordinator = new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    return "issued-credential";
                }
        );
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(coordinator);

        Response response = interceptor.intercept(new RecordingChain(401));

        assertEquals(401, response.code());
        assertEquals(1, registrations.get());
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private String credential;

        @Override
        public String getCredential() {
            return credential;
        }

        @Override
        public void saveCredential(String credential) {
            this.credential = credential;
        }

        @Override
        public void clearCredential() {
            credential = null;
        }
    }

    private static final class RecordingChain implements Interceptor.Chain {
        private final int responseCode;
        private final Request initialRequest = new Request.Builder()
                .url("http://localhost/predict")
                .build();
        private Request proceededRequest;

        private RecordingChain(int responseCode) {
            this.responseCode = responseCode;
        }

        @Override
        public Request request() {
            return initialRequest;
        }

        @Override
        public Response proceed(Request request) {
            proceededRequest = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("test")
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 1000;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 1000;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 1000;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
