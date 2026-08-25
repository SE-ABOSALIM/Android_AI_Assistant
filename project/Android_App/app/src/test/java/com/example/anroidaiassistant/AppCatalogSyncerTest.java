package com.example.anroidaiassistant;

import static org.junit.Assert.assertEquals;

import com.example.anroidaiassistant.api.dto.AppCatalogEntry;
import com.example.anroidaiassistant.apps.LaunchableApp;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AppCatalogSyncerTest {
    @Test
    public void catalogContainsLauncherVisibleSystemAndThirdPartyApps() {
        List<AppCatalogEntry> entries = AppCatalogSyncer.buildCatalogEntries(Arrays.asList(
                new LaunchableApp("YouTube", "com.google.android.youtube"),
                new LaunchableApp("Settings", "com.android.settings")
        ));

        assertEquals(2, entries.size());
        assertEquals("com.android.settings", entries.get(0).getPackageName());
        assertEquals("Settings", entries.get(0).getLabel());
        assertEquals("com.google.android.youtube", entries.get(1).getPackageName());
    }
}
