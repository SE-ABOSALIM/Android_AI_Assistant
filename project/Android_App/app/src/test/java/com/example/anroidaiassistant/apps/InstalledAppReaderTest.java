package com.example.anroidaiassistant.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InstalledAppReaderTest {
    @Test
    public void launcherQuery_isUsedForInstalledAppDiscovery() {
        List<String> observedQuery = new ArrayList<>();
        InstalledAppReader reader = new InstalledAppReader((action, category) -> {
            observedQuery.add(action);
            observedQuery.add(category);
            return Collections.singletonList(new LaunchableApp("Settings", "com.android.settings"));
        });

        List<LaunchableApp> apps = reader.getLaunchableApps(null);

        assertEquals(Arrays.asList(
                "android.intent.action.MAIN",
                "android.intent.category.LAUNCHER"
        ), observedQuery);
        assertEquals(1, apps.size());
    }

    @Test
    public void launchableApps_remainDiscoverableWithoutBroadVisibility() {
        InstalledAppReader reader = new InstalledAppReader((action, category) -> Arrays.asList(
                new LaunchableApp("Settings", "com.android.settings"),
                new LaunchableApp("YouTube", "com.google.android.youtube")
        ));

        List<LaunchableApp> apps = reader.getLaunchableApps(null);

        assertEquals(2, apps.size());
        assertEquals("com.android.settings", apps.get(0).getPackageName());
        assertEquals("com.google.android.youtube", apps.get(1).getPackageName());
    }

    @Test
    public void nonLaunchablePackages_areNotRequiredForCatalogMatching() {
        InstalledAppReader reader = new InstalledAppReader((action, category) ->
                Collections.singletonList(
                        new LaunchableApp("YouTube", "com.google.android.youtube")
                )
        );
        AppMatcher matcher = new AppMatcher(reader);

        assertEquals(1, matcher.findAppMatches(null, "YouTube").size());
        assertTrue(matcher.findAppMatches(null, "Background Agent").isEmpty());
    }
}
