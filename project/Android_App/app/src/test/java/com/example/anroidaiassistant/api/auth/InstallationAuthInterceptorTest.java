package com.example.anroidaiassistant.api.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

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

        Response response = interceptor.intercept(chain);
        response.close();

        assertEquals(1, registrations.get());
        assertEquals("Bearer issued-credential", chain.requestAt(0).header("Authorization"));
        assertEquals("issued-credential", store.getCredential());
    }

    @Test
    public void single401_rotatesCredentialAndRetriesOnce() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "credential-2")
        );
        RecordingChain chain = new RecordingChain(401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals(2, chain.proceedCount());
        assertEquals("Bearer credential-1", chain.requestAt(0).header("Authorization"));
        assertEquals("Bearer credential-2", chain.requestAt(1).header("Authorization"));
        assertEquals("credential-2", store.getCredential());
        assertEquals(1, registrations.get());
        assertEquals(1, store.clearCount);
        assertTrue(chain.bodyAt(0).closed);
        response.close();
    }

    @Test
    public void second401_afterRetry_doesNotLoop() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "credential-2")
        );
        RecordingChain chain = new RecordingChain(401, 401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(401, response.code());
        assertEquals(2, chain.proceedCount());
        assertEquals(1, registrations.get());
        assertTrue(chain.bodyAt(0).closed);
        response.close();
    }

    @Test
    public void concurrent401s_triggerSingleCredentialRotation() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "credential-2")
        );
        CountDownLatch bothOriginalRequestsStarted = new CountDownLatch(2);
        CountDownLatch releaseUnauthorizedResponses = new CountDownLatch(1);
        ProceedHook synchronizeUnauthorizedResponses = () -> {
            bothOriginalRequestsStarted.countDown();
            await(releaseUnauthorizedResponses);
        };
        RecordingChain firstChain = new RecordingChain(
                defaultRequest(), synchronizeUnauthorizedResponses, 401, 200
        );
        RecordingChain secondChain = new RecordingChain(
                defaultRequest(), synchronizeUnauthorizedResponses, 401, 200
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Response> first = executor.submit(() -> interceptor.intercept(firstChain));
            Future<Response> second = executor.submit(() -> interceptor.intercept(secondChain));
            assertTrue(bothOriginalRequestsStarted.await(5, TimeUnit.SECONDS));
            releaseUnauthorizedResponses.countDown();
            Response firstResponse = first.get();
            Response secondResponse = second.get();

            assertEquals(200, firstResponse.code());
            assertEquals(200, secondResponse.code());
            assertEquals(1, registrations.get());
            assertEquals(1, store.clearCount);
            assertEquals("Bearer credential-2", firstChain.requestAt(1).header("Authorization"));
            assertEquals("Bearer credential-2", secondChain.requestAt(1).header("Authorization"));
            firstResponse.close();
            secondResponse.close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void stale401ForOldCredential_doesNotRotateNewCredential() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "must-not-register")
        );
        RecordingChain chain = new RecordingChain(
                defaultRequest(),
                () -> store.saveCredential("credential-2"),
                401,
                200
        );

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals(0, registrations.get());
        assertEquals(0, store.clearCount);
        assertEquals("credential-2", store.getCredential());
        assertEquals("Bearer credential-2", chain.requestAt(1).header("Authorization"));
        response.close();
    }

    @Test
    public void successful401Recovery_replaysOriginalRequestOnce() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "credential-2")
        );
        Request original = new Request.Builder()
                .url("http://localhost/custom-commands?language=TR")
                .get()
                .build();
        RecordingChain chain = new RecordingChain(original, null, 401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(2, chain.proceedCount());
        assertEquals(original.url(), chain.requestAt(1).url());
        assertEquals(original.method(), chain.requestAt(1).method());
        assertEquals(1, registrations.get());
        response.close();
    }

    @Test
    public void non401Response_doesNotTriggerCredentialRecovery() throws Exception {
        assertNoRecoveryForResponse(500);
    }

    @Test
    public void forbidden403_doesNotTriggerCredentialRecovery() throws Exception {
        assertNoRecoveryForResponse(403);
    }

    @Test
    public void oneShotRequestBody_isNotReplayedAfter401() throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "must-not-register")
        );
        Request request = new Request.Builder()
                .url("http://localhost/predict")
                .post(new OneShotRequestBody())
                .build();
        RecordingChain chain = new RecordingChain(request, null, 401, 200);

        Response response = interceptor.intercept(chain);

        assertEquals(401, response.code());
        assertEquals(1, chain.proceedCount());
        assertEquals(0, registrations.get());
        assertEquals("credential-1", store.getCredential());
        assertFalse(chain.bodyAt(0).closed);
        response.close();
    }

    private static InstallationAuthCoordinator coordinator(
            InMemoryCredentialStore store,
            AtomicInteger registrations,
            String replacementCredential
    ) {
        return new InstallationAuthCoordinator(
                store,
                () -> {
                    registrations.incrementAndGet();
                    return replacementCredential;
                }
        );
    }

    private static void assertNoRecoveryForResponse(int responseCode) throws Exception {
        InMemoryCredentialStore store = new InMemoryCredentialStore("credential-1");
        AtomicInteger registrations = new AtomicInteger();
        InstallationAuthInterceptor interceptor = new InstallationAuthInterceptor(
                coordinator(store, registrations, "must-not-register")
        );
        RecordingChain chain = new RecordingChain(responseCode);

        Response response = interceptor.intercept(chain);

        assertEquals(responseCode, response.code());
        assertEquals(1, chain.proceedCount());
        assertEquals(0, registrations.get());
        assertEquals(0, store.clearCount);
        assertEquals("credential-1", store.getCredential());
        response.close();
    }

    private static Request defaultRequest() {
        return new Request.Builder()
                .url("http://localhost/predict")
                .build();
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for test coordination", exception);
        }
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private volatile String credential;
        private int clearCount;

        private InMemoryCredentialStore() {}

        private InMemoryCredentialStore(String credential) {
            this.credential = credential;
        }

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
            clearCount++;
            credential = null;
        }
    }

    private interface ProceedHook {
        void run() throws IOException;
    }

    private static final class RecordingChain implements Interceptor.Chain {
        private final int[] responseCodes;
        private final Request initialRequest;
        private final ProceedHook firstProceedHook;
        private final AtomicInteger proceeds = new AtomicInteger();
        private final List<Request> proceededRequests = Collections.synchronizedList(new ArrayList<>());
        private final List<TrackingResponseBody> responseBodies = Collections.synchronizedList(new ArrayList<>());

        private RecordingChain(int... responseCodes) {
            this(defaultRequest(), null, responseCodes);
        }

        private RecordingChain(
                Request initialRequest,
                ProceedHook firstProceedHook,
                int... responseCodes
        ) {
            this.initialRequest = initialRequest;
            this.firstProceedHook = firstProceedHook;
            this.responseCodes = responseCodes;
        }

        @Override
        public Request request() {
            return initialRequest;
        }

        @Override
        public Response proceed(Request request) throws IOException {
            int index = proceeds.getAndIncrement();
            proceededRequests.add(request);
            if (index == 0 && firstProceedHook != null) {
                firstProceedHook.run();
            }
            int responseCode = responseCodes[Math.min(index, responseCodes.length - 1)];
            TrackingResponseBody body = new TrackingResponseBody();
            responseBodies.add(body);
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("test")
                    .body(body)
                    .build();
        }

        private int proceedCount() {
            return proceeds.get();
        }

        private Request requestAt(int index) {
            return proceededRequests.get(index);
        }

        private TrackingResponseBody bodyAt(int index) {
            return responseBodies.get(index);
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

    private static final class TrackingResponseBody extends ResponseBody {
        private final Buffer buffer = new Buffer();
        private boolean closed;

        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public BufferedSource source() {
            return buffer;
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    private static final class OneShotRequestBody extends RequestBody {
        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public void writeTo(BufferedSink sink) {}

        @Override
        public boolean isOneShot() {
            return true;
        }
    }
}
