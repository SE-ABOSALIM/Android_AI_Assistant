package com.example.anroidaiassistant;

import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.api.dto.PredictResponse;
import com.example.anroidaiassistant.session.AssistantSession;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Spinner spinnerLanguage;
    private TextView tvResult;
    private Button btnSpeak;
    private EditText etCommand;
    private Button btnPredict;
    private String selectedLanguage = "TR";
    private ApiService apiService;
    private CommandExecutor commandExecutor;
    private boolean isServiceListening = false;
    private boolean isCatalogSyncInProgress = false;
    private Call<AppCatalogResponse> appCatalogSyncCall;
    private AlertDialog spellingSuggestionDialog;
    private Runnable pendingPermissionAction;

    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    private static final int REQUEST_CODE_RUNTIME_PERMISSIONS = 200;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        tvResult = findViewById(R.id.tvResult);
        btnSpeak = findViewById(R.id.btnSpeak);
        etCommand = findViewById(R.id.etCommand);
        btnPredict = findViewById(R.id.btnPredict);
        apiService = RetrofitClient.getClient().create(ApiService.class);
        commandExecutor = new CommandExecutor(this);

        String[] languages = {"TR", "EN", "AR"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                languages
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = languages[position];
                MyAccessibilityService service = MyAccessibilityService.getInstance();
                if (service != null) {
                    service.updateLanguage(selectedLanguage);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleListening();
            }
        });

        btnPredict.setOnClickListener(view -> triggerTestCommand());

        checkOverlayPermission();
        if (!isAccessibilityServiceEnabled()) {
            checkAccessibilityPermission();
        }
        refreshListeningUiState();
    }

    private void triggerTestCommand() {
        String commandText = etCommand.getText() == null
                ? ""
                : etCommand.getText().toString().trim();

        if (commandText.isEmpty()) {
            Toast.makeText(this, "Type a command first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ensureRuntimePermissions(this::triggerTestCommand)) {
            return;
        }

        ensureAppCatalogThen(() -> sendManualPredictionRequest(commandText));
    }

    private void sendManualPredictionRequest(String text) {
        PredictRequest request = new PredictRequest(text, selectedLanguage, AssistantSession.getSessionId());
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
                showAssistantMessage("No response from backend");
            }

            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                Log.e(TAG, "Manual prediction request failed", t);
                showAssistantMessage("Backend unavailable");
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
        instance = null;
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ekran ustu izin gerekli", Toast.LENGTH_SHORT).show();
            // Temporarily disabled: do not redirect to the "Appear on top" settings screen.
            // Intent intent = new Intent(
            //         Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            //         Uri.parse("package:" + getPackageName())
            // );
            // startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
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

    private void checkAccessibilityPermission() {
        Toast.makeText(
                this,
                "Lutfen erisilebilirlik servisini kapatip tekrar acin.",
                Toast.LENGTH_LONG
        ).show();
        // Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // startActivity(intent);
    }

    private void toggleListening() {
        if (isCatalogSyncInProgress) {
            Toast.makeText(this, "Uygulama listesi gonderiliyor...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ensureRuntimePermissions(this::toggleListening)) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            checkOverlayPermission();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            checkAccessibilityPermission();
            return;
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(
                    this,
                    "Servis bagli degil. Ayarlardan servisi kapatip tekrar acin.",
                    Toast.LENGTH_LONG
            ).show();
            checkAccessibilityPermission();
            return;
        }

        if (!isServiceListening) {
            service.updateLanguage(selectedLanguage);
            service.startContinuousListening();
            isServiceListening = true;
            refreshListeningUiState();
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
        if (AssistantSession.isCatalogReadyForLanguage(selectedLanguage)) {
            onCatalogReady.run();
            return;
        }

        String sessionId = AssistantSession.startNewSession();
        syncAppCatalogForSession(sessionId, onCatalogReady);
    }

    private void syncAppCatalogForSession(String sessionId, Runnable onSuccess) {
        isCatalogSyncInProgress = true;
        btnSpeak.setEnabled(false);
        tvResult.setText("Uygulama listesi gonderiliyor...");

        appCatalogSyncCall = AppCatalogSyncer.syncInstalledApps(this, apiService, sessionId, selectedLanguage, (success, message) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (!sessionId.equals(AssistantSession.getSessionId())) {
                return;
            }

            isCatalogSyncInProgress = false;
            appCatalogSyncCall = null;
            btnSpeak.setEnabled(true);

            if (success) {
                onSuccess.run();
                return;
            }

            closeCurrentBackendSession();
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            refreshListeningUiState();
        }));

        if (appCatalogSyncCall == null) {
            isCatalogSyncInProgress = false;
            btnSpeak.setEnabled(true);
            closeCurrentBackendSession();
            refreshListeningUiState();
        }
    }

    private void cancelAppCatalogSyncIfNeeded() {
        if (appCatalogSyncCall != null) {
            appCatalogSyncCall.cancel();
            appCatalogSyncCall = null;
        }

        if (isCatalogSyncInProgress) {
            isCatalogSyncInProgress = false;
            closeCurrentBackendSession();
        }
    }

    private void closeCurrentBackendSession() {
        String endedSessionId = AssistantSession.endSession();
        AppCatalogSyncer.closeSession(apiService, endedSessionId);
    }

    private void refreshListeningUiState() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        isServiceListening = service != null && service.isContinuousListeningActive();

        if (isServiceListening) {
            btnSpeak.setText("Durdur");
            btnSpeak.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            );
            tvResult.setText("Arka planda dinleniyor...");
        } else {
            btnSpeak.setText("Konus (Baslat)");
            btnSpeak.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#6200EE")
                    )
            );
            tvResult.setText("Dinleme durduruldu.");
        }
    }

    public void syncListeningUiState() {
        runOnUiThread(this::refreshListeningUiState);
    }

    public void showAssistantMessage(String message) {
        runOnUiThread(() -> tvResult.setText(message));
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
                    .setTitle("Uygulamayi acamiyor musun?")
                    .setMessage(
                            "Uygulama adini harf harf soylemeyi dene.\n\n"
                                    + "Can't open the app?\n"
                                    + "Try spelling the app name letter by letter."
                    )
                    .setPositiveButton("Try spelling", (dialog, which) -> {
                        MyAccessibilityService service = MyAccessibilityService.getInstance();
                        if (service != null) {
                            service.enableSpellAppMode();
                            tvResult.setText("Spell mode aktif. Uygulama adini harf harf soyle.");
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create();

            spellingSuggestionDialog.setOnDismissListener(dialog -> spellingSuggestionDialog = null);
            spellingSuggestionDialog.show();
        });
    }

    public void updateResultUI(PredictResponse response) {
        runOnUiThread(() -> {
            String responseInfo = "intent: " + response.getIntent() + "\n"
                    + "language: " + response.getLanguage() + "\n"
                    + "confidence: " + response.getConfidence() + "\n"
                    + "accepted: " + response.isAccepted() + "\n"
                    + "parameters: " + response.getParameters() + "\n"
                    + "missing_slots: " + response.getMissingSlots() + "\n"
                    + "error_code: " + response.getErrorCode() + "\n"
                    + "error_message: " + response.getErrorMessage();
            tvResult.setText(responseInfo);
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

            Toast.makeText(this, "Mikrofon, rehber ve arama izinleri gerekli", Toast.LENGTH_SHORT).show();
        }
    }
}
