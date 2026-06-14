package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.api.dto.AppCatalogStatusResponse;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.ui.screens.CustomCommandsFragment;
import com.example.anroidaiassistant.ui.screens.GuideFragment;
import com.example.anroidaiassistant.ui.screens.HomeFragment;
import com.example.anroidaiassistant.ui.screens.PermissionsFragment;
import com.example.anroidaiassistant.ui.screens.SettingsFragment;
import com.example.anroidaiassistant.util.DeviceIdentity;

import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private String selectedLanguage = "TR";
    private ApiService apiService;
    private CommandExecutor commandExecutor;
    private boolean isServiceListening = false;
    private boolean isCatalogSyncInProgress = false;
    private boolean isCatalogStatusCheckInProgress = false;
    private Call<AppCatalogResponse> appCatalogSyncCall;
    private Call<AppCatalogStatusResponse> appCatalogStatusCall;
    private final List<Runnable> pendingCatalogReadyActions = new ArrayList<>();
    private AlertDialog spellingSuggestionDialog;
    private Runnable pendingPermissionAction;
    private HomeFragment homeFragment;
    private BottomNavigationView bottomNavigationView;

    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    private static final int REQUEST_CODE_RUNTIME_PERMISSIONS = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AssistantSettings.applySavedTheme(this);
        AssistantSettings.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        selectedLanguage = AssistantSettings.getLanguage(this);
        setContentView(R.layout.activity_main);
        instance = this;

        apiService = RetrofitClient.getClient().create(ApiService.class);
        commandExecutor = new CommandExecutor(this);
        setupBottomNavigation(savedInstanceState);

        refreshListeningUiState();
        warmUpAppCatalog();
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            showSection(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void showSection(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_permissions) {
            fragment = new PermissionsFragment();
        } else if (itemId == R.id.nav_custom_commands) {
            fragment = new CustomCommandsFragment();
        } else if (itemId == R.id.nav_guide) {
            fragment = new GuideFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else {
            fragment = new HomeFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_content_container, fragment)
                .commit();
    }

    public void showPermissionsPage() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_permissions);
        } else {
            showSection(R.id.nav_permissions);
        }
    }

    public void updateSelectedLanguage(String language) {
        selectedLanguage = AssistantSettings.normalizeLanguage(language);
        AssistantSettings.setLanguage(this, selectedLanguage);

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            service.updateLanguage(selectedLanguage);
        }
        warmUpAppCatalog();
    }

    public void attachHomeFragment(HomeFragment fragment) {
        homeFragment = fragment;
        refreshListeningUiState();
    }

    public void detachHomeFragment(HomeFragment fragment) {
        if (homeFragment == fragment) {
            homeFragment = null;
        }
    }

    public void toggleListeningFromHome() {
        toggleListening();
    }

    public void runManualCommandFromHome(String commandText) {
        triggerTestCommand(commandText);
    }

    private void triggerTestCommand(String commandText) {
        commandText = commandText == null ? "" : commandText.trim();

        if (commandText.isEmpty()) {
            Toast.makeText(this, "Type a command first", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalCommandText = commandText;
        if (!ensureRuntimePermissions(() -> triggerTestCommand(finalCommandText))) {
            return;
        }

        ensureAppCatalogThen(() -> sendManualPredictionRequest(finalCommandText));
    }

    private void sendManualPredictionRequest(String text) {
        PredictRequest request = new PredictRequest(
                text,
                selectedLanguage,
                AssistantSession.getSessionId(),
                DeviceIdentity.getDeviceId(this),
                null,
                false
        );
        apiService.predict(request).enqueue(new Callback<PredictResponse>() {
            @Override
            public void onResponse(Call<PredictResponse> call, Response<PredictResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PredictResponse body = response.body();
                    updateResultUI(body);
                    commandExecutor.executeCommand(body);
                    return;
                }

                Log.e(TAG, "Manual prediction failed. httpCode=" + response.code());
                showAssistantMessage(getString(R.string.backend_no_response));
            }

            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                Log.e(TAG, "Manual prediction request failed", t);
                showAssistantMessage(getString(R.string.backend_unavailable));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service != null) {
            service.updateLanguage(selectedLanguage);
        }
        refreshListeningUiState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAppCatalogSyncIfNeeded();
        if (spellingSuggestionDialog != null) {
            spellingSuggestionDialog.dismiss();
        }
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null || !service.isContinuousListeningActive()) {
            closeCurrentBackendSession();
        }
        instance = null;
    }

    public boolean isAssistantAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }

        return enabledServices.contains(getPackageName())
                && enabledServices.contains(MyAccessibilityService.class.getSimpleName());
    }

    private boolean hasRequiredAssistantPermissions() {
        return missingRuntimePermissions().isEmpty()
                && Settings.canDrawOverlays(this)
                && isAssistantAccessibilityServiceEnabled();
    }

    private List<String> missingRuntimePermissions() {
        List<String> missingPermissions = new ArrayList<>();
        addMissingPermission(missingPermissions, android.Manifest.permission.RECORD_AUDIO);
        addMissingPermission(missingPermissions, android.Manifest.permission.READ_CONTACTS);
        addMissingPermission(missingPermissions, android.Manifest.permission.CALL_PHONE);
        addMissingPermission(missingPermissions, android.Manifest.permission.READ_PHONE_STATE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            addMissingPermission(missingPermissions, android.Manifest.permission.ANSWER_PHONE_CALLS);
        }
        addMissingPermission(missingPermissions, android.Manifest.permission.CAMERA);
        return missingPermissions;
    }

    private void showPermissionRequiredDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_required_title)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.permission_required_action, (dialog, which) -> showPermissionsPage())
                .setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void toggleListening() {
        if (!hasRequiredAssistantPermissions()) {
            showPermissionRequiredDialog();
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(
                    this,
                    R.string.accessibility_service_disconnected,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!isServiceListening) {
            ensureAppCatalogThen(() -> {
                service.updateLanguage(selectedLanguage);
                service.startContinuousListening();
                isServiceListening = true;
                refreshListeningUiState();
            });
        } else {
            service.stopContinuousListening();
            isServiceListening = false;
            refreshListeningUiState();
        }
    }

    private boolean ensureRuntimePermissions(Runnable onGranted) {
        List<String> missingPermissions = new ArrayList<>();
        addMissingPermission(missingPermissions, android.Manifest.permission.RECORD_AUDIO);
        addMissingPermission(missingPermissions, android.Manifest.permission.READ_CONTACTS);
        addMissingPermission(missingPermissions, android.Manifest.permission.CALL_PHONE);
        addMissingPermission(missingPermissions, android.Manifest.permission.READ_PHONE_STATE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            addMissingPermission(missingPermissions, android.Manifest.permission.ANSWER_PHONE_CALLS);
        }
        addMissingPermission(missingPermissions, android.Manifest.permission.CAMERA);

        if (missingPermissions.isEmpty()) {
            return true;
        }

        pendingPermissionAction = onGranted;
        requestPermissions(
                missingPermissions.toArray(new String[0]),
                REQUEST_CODE_RUNTIME_PERMISSIONS
        );
        return false;
    }

    private void addMissingPermission(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void ensureAppCatalogThen(Runnable onCatalogReady) {
        if (onCatalogReady != null) {
            pendingCatalogReadyActions.add(onCatalogReady);
        }

        if (AssistantSession.isCatalogReadyForLanguage(selectedLanguage)) {
            verifyExistingAppCatalogThen();
            return;
        }

        startAppCatalogSyncIfNeeded(true);
    }

    private void warmUpAppCatalog() {
        if (AssistantSession.isCatalogReadyForLanguage(selectedLanguage)) {
            return;
        }
        startAppCatalogSyncIfNeeded(false);
    }

    private void verifyExistingAppCatalogThen() {
        if (isCatalogStatusCheckInProgress) {
            return;
        }

        String sessionId = AssistantSession.getSessionId();
        if (sessionId == null) {
            startAppCatalogSyncIfNeeded(true);
            return;
        }

        isCatalogStatusCheckInProgress = true;
        refreshListeningUiState();
        appCatalogStatusCall = apiService.getAppCatalogStatus(sessionId);
        appCatalogStatusCall.enqueue(new Callback<AppCatalogStatusResponse>() {
            @Override
            public void onResponse(Call<AppCatalogStatusResponse> call, Response<AppCatalogStatusResponse> response) {
                runOnUiThread(() -> {
                    isCatalogStatusCheckInProgress = false;
                    appCatalogStatusCall = null;
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    AppCatalogStatusResponse body = response.body();
                    boolean catalogAvailable = response.isSuccessful()
                            && body != null
                            && body.isAccepted()
                            && body.isAvailable();
                    if (catalogAvailable) {
                        runPendingCatalogReadyActions();
                    } else {
                        startAppCatalogSyncIfNeeded(true);
                    }
                    refreshListeningUiState();
                });
            }

            @Override
            public void onFailure(Call<AppCatalogStatusResponse> call, Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                runOnUiThread(() -> {
                    isCatalogStatusCheckInProgress = false;
                    appCatalogStatusCall = null;
                    startAppCatalogSyncIfNeeded(true);
                    refreshListeningUiState();
                });
            }
        });
    }

    private void startAppCatalogSyncIfNeeded(boolean reportFailure) {
        if (isCatalogSyncInProgress) {
            refreshListeningUiState();
            return;
        }

        String sessionId = AssistantSession.getOrCreateSessionId();
        syncAppCatalogForSession(sessionId, reportFailure);
    }

    private void syncAppCatalogForSession(String sessionId, boolean reportFailure) {
        isCatalogSyncInProgress = true;
        if (homeFragment != null) {
            homeFragment.setSpeakButtonEnabled(false);
            homeFragment.setBackendState(getString(R.string.backend_state_syncing));
            homeFragment.setStatusText(getString(R.string.catalog_syncing));
        }

        appCatalogSyncCall = AppCatalogSyncer.syncInstalledApps(this, apiService, sessionId, selectedLanguage, (success, message) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (!sessionId.equals(AssistantSession.getSessionId())) {
                return;
            }

            isCatalogSyncInProgress = false;
            appCatalogSyncCall = null;
            if (homeFragment != null) {
                homeFragment.setSpeakButtonEnabled(true);
                homeFragment.setBackendState(success
                        ? getString(R.string.backend_state_ready)
                        : getString(R.string.backend_state_error));
            }

            if (success) {
                if (!AssistantSession.isCatalogReadyForLanguage(selectedLanguage)) {
                    startAppCatalogSyncIfNeeded(reportFailure);
                    return;
                }
                runPendingCatalogReadyActions();
                return;
            }

            clearPendingCatalogReadyActions();
            if (reportFailure) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            refreshListeningUiState();
        }));

        if (appCatalogSyncCall == null) {
            isCatalogSyncInProgress = false;
            if (homeFragment != null) {
                homeFragment.setSpeakButtonEnabled(true);
                homeFragment.setBackendState(getString(R.string.backend_state_error));
            }
            clearPendingCatalogReadyActions();
            if (reportFailure) {
                Toast.makeText(this, R.string.backend_unavailable, Toast.LENGTH_SHORT).show();
            }
            refreshListeningUiState();
        }
    }

    private void cancelAppCatalogSyncIfNeeded() {
        if (appCatalogStatusCall != null) {
            appCatalogStatusCall.cancel();
            appCatalogStatusCall = null;
        }
        isCatalogStatusCheckInProgress = false;

        if (appCatalogSyncCall != null) {
            appCatalogSyncCall.cancel();
            appCatalogSyncCall = null;
        }

        if (isCatalogSyncInProgress) {
            isCatalogSyncInProgress = false;
        }
        clearPendingCatalogReadyActions();
    }

    private void runPendingCatalogReadyActions() {
        List<Runnable> actions = new ArrayList<>(pendingCatalogReadyActions);
        pendingCatalogReadyActions.clear();
        for (Runnable action : actions) {
            if (action != null) {
                action.run();
            }
        }
    }

    private void clearPendingCatalogReadyActions() {
        pendingCatalogReadyActions.clear();
    }

    private void closeCurrentBackendSession() {
        String endedSessionId = AssistantSession.endSession();
        AppCatalogSyncer.closeSession(apiService, endedSessionId);
    }

    private void refreshListeningUiState() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        isServiceListening = service != null && service.isContinuousListeningActive();

        if (homeFragment != null) {
            homeFragment.setListeningState(isServiceListening);
            boolean catalogBusy = isCatalogSyncInProgress || isCatalogStatusCheckInProgress;
            homeFragment.setSpeakButtonEnabled(!catalogBusy);
            homeFragment.setBackendState(catalogBusy
                    ? getString(R.string.backend_state_syncing)
                    : getString(R.string.backend_state_ready));
            homeFragment.setStatusText(isServiceListening
                    ? getString(R.string.home_status_background_listening)
                    : getString(R.string.home_status_stopped));
        }
    }

    public void syncListeningUiState() {
        runOnUiThread(this::refreshListeningUiState);
    }

    public void showAssistantMessage(String message) {
        runOnUiThread(() -> {
            if (homeFragment != null) {
                homeFragment.setStatusText(message);
            }
        });
    }

    public void showSpellingSuggestionDialog() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (spellingSuggestionDialog != null && spellingSuggestionDialog.isShowing()) {
                return;
            }

            spellingSuggestionDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.spell_suggestion_title)
                    .setMessage(R.string.spell_suggestion_message)
                    .setPositiveButton(R.string.spell_suggestion_action, (dialog, which) -> {
                        MyAccessibilityService service = MyAccessibilityService.getInstance();
                        if (service != null) {
                            service.enableSpellAppMode();
                            showAssistantMessage(getString(R.string.spell_mode_enabled));
                        }
                    })
                    .setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.dismiss())
                    .create();

            spellingSuggestionDialog.setOnDismissListener(dialog -> spellingSuggestionDialog = null);
            spellingSuggestionDialog.show();
        });
    }

    public void updateResultUI(PredictResponse response) {
        runOnUiThread(() -> {
            if (homeFragment != null) {
                homeFragment.showPredictionResult(response);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RUNTIME_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int grantResult : grantResults) {
                if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            Runnable action = pendingPermissionAction;
            pendingPermissionAction = null;
            if (allGranted && action != null) {
                action.run();
                return;
            }

            Toast.makeText(this, R.string.runtime_permissions_required, Toast.LENGTH_SHORT).show();
        }
    }
}
