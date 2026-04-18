package com.example.anroidaiassistant;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerLanguage;
    private TextView tvResult;
    private Button btnSpeak;
    private boolean isServiceListening = false;

    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        tvResult = findViewById(R.id.tvResult);
        btnSpeak = findViewById(R.id.btnSpeak);

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
                MyAccessibilityService service = MyAccessibilityService.getInstance();
                if (service != null) {
                    service.updateLanguage(languages[position]);
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

        checkOverlayPermission();
        
        if (!isAccessibilityServiceEnabled()) {
            checkAccessibilityPermission();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        return enabledServices.contains(getPackageName()) && enabledServices.contains(MyAccessibilityService.class.getSimpleName());
    }

    private void checkAccessibilityPermission() {
        Toast.makeText(this, "Lütfen Erişilebilirlik Servisini kapatıp tekrar açın.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void toggleListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
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
        if (service != null) {
            if (!isServiceListening) {
                service.startContinuousListening();
                btnSpeak.setText("Durdur");
                btnSpeak.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                tvResult.setText("Arka planda dinleniyor...");
                isServiceListening = true;
            } else {
                service.stopContinuousListening();
                btnSpeak.setText("Konuş (Başlat)");
                btnSpeak.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#6200EE")));
                tvResult.setText("Dinleme durduruldu.");
                isServiceListening = false;
            }
        } else {
            Toast.makeText(this, "Servis bağlı değil! Lütfen ayarlardan servisi KAPATIP tekrar AÇIN.", Toast.LENGTH_LONG).show();
            checkAccessibilityPermission();
        }
    }

    public void updateResultUI(PredictResponse response) {
        runOnUiThread(() -> {
            String debugInfo = "Intent: " + response.getIntent() + "\n" +
                             "Params: " + response.getParameters() + "\n" +
                             "Confidence: " + String.format("%.4f", response.getConfidence()) + "\n" +
                             "Accepted: " + response.isAccepted() + "\n" +
                             "Temp: " + response.getTemperature();
            tvResult.setText(debugInfo);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toggleListening();
            } else {
                Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
