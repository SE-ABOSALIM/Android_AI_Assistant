package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.session.AssistantSession;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import retrofit2.http.GET;

public class AppCatalogStatusContractTest {
    @Test
    public void authenticatedStatusLookup_doesNotUseSessionIdAsOwner() throws Exception {
        Method method = ApiService.class.getMethod("getAppCatalogStatus");
        GET get = method.getAnnotation(GET.class);

        assertNotNull(get);
        assertEquals("app-catalog/status", get.value());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    public void sameDevice_newSession_doesNotForceFullCatalogSync() {
        AssistantSession.startNewSession();
        AssistantSession.setCatalogVersion("catalog-v1", "TR");
        AssistantSession.startNewSession();

        assertTrue(AssistantSession.isCatalogReadyForLanguage("TR"));
        assertFalse(AppCatalogSyncer.requiresCatalogSync(
                true,
                true,
                "catalog-v1",
                "TR",
                "catalog-v1",
                "TR"
        ));

        AssistantSession.endSession();
    }

    @Test
    public void newDevice_withoutCatalog_requiresInitialSync() {
        assertTrue(AppCatalogSyncer.requiresCatalogSync(
                true,
                false,
                null,
                null,
                "catalog-v1",
                "TR"
        ));
    }

    @Test
    public void changedCatalogVersion_stillTriggersSync() {
        assertTrue(AppCatalogSyncer.requiresCatalogSync(
                true,
                true,
                "catalog-v1",
                "TR",
                "catalog-v2",
                "TR"
        ));
    }

    @Test
    public void currentCatalogVersion_doesNotTriggerFullSync() {
        assertFalse(AppCatalogSyncer.requiresCatalogSync(
                true,
                true,
                "catalog-v1",
                "TR",
                "catalog-v1",
                "tr"
        ));
    }

    @Test
    public void changedCatalogLanguage_stillTriggersSync() {
        assertTrue(AppCatalogSyncer.requiresCatalogSync(
                true,
                true,
                "catalog-v1",
                "AR",
                "catalog-v1",
                "TR"
        ));
    }

    @Test
    public void warmUp_checksDeviceStatusBeforeStartingFullSync() throws Exception {
        String mainActivity = new String(
                Files.readAllBytes(appRoot().resolve(
                        "src/main/java/com/example/anroidaiassistant/MainActivity.java"
                )),
                StandardCharsets.UTF_8
        );
        String warmUp = between(
                mainActivity,
                "private void warmUpAppCatalog()",
                "private void verifyExistingAppCatalogThen(boolean reportFailure)"
        );
        String verification = between(
                mainActivity,
                "private void verifyExistingAppCatalogThen(boolean reportFailure)",
                "private void startAppCatalogSyncIfNeeded(boolean reportFailure)"
        );

        assertTrue(warmUp.contains("verifyExistingAppCatalogThen(false);"));
        assertFalse(warmUp.contains("startAppCatalogSyncIfNeeded(false);"));
        assertTrue(verification.contains("apiService.getAppCatalogStatus()"));
        assertTrue(verification.contains("AppCatalogSyncer.requiresCatalogSync("));
        assertFalse(verification.contains("AssistantSession.getSessionId()"));
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0);
        assertTrue(endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private Path appRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
    }
}
