package com.example.anroidaiassistant.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.anroidaiassistant.executor.CommandExecutionContext;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AppLauncherTest {
    @Test
    public void openApp_worksWithCatalogResolvedPackage() {
        RecordingGateway gateway = new RecordingGateway();
        AppLauncher launcher = new AppLauncher(gateway);

        boolean launched = launcher.launchPackage(
                null,
                "com.google.android.youtube",
                "YouTube",
                new CommandExecutionContext(null, ignored -> {})
        );

        assertTrue(launched);
        assertEquals(List.of("launch:com.google.android.youtube"), gateway.actions);
    }

    @Test
    public void appInfoAndUninstall_knownPackagePathsRemainValid() {
        RecordingGateway gateway = new RecordingGateway();
        AppLauncher launcher = new AppLauncher(gateway);
        CommandExecutionContext context = new CommandExecutionContext(null, ignored -> {});

        assertTrue(launcher.openAppInfo(null, "com.google.android.youtube", "YouTube", context));
        assertTrue(launcher.requestUninstallPackage(
                null,
                "com.google.android.youtube",
                "YouTube",
                context
        ));

        assertEquals(List.of(
                "info:com.google.android.youtube",
                "uninstall:com.google.android.youtube"
        ), gateway.actions);
    }

    private static final class RecordingGateway implements AppLauncher.PackageActionGateway {
        private final List<String> actions = new ArrayList<>();

        @Override
        public boolean launch(Context context, String packageName) {
            actions.add("launch:" + packageName);
            return true;
        }

        @Override
        public boolean openAppInfo(Context context, String packageName) {
            actions.add("info:" + packageName);
            return true;
        }

        @Override
        public boolean requestUninstall(Context context, String packageName) {
            actions.add("uninstall:" + packageName);
            return true;
        }
    }
}
