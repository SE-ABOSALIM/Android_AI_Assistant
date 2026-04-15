package com.example.anroidaiassistant;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private EditText etCommand;
    private Spinner spinnerLanguage;
    private Button btnPredict;
    private TextView tvResult;

    private ApiService apiService;

    private Button btnSpeak;

    private static final int REQUEST_CODE_SPEECH_INPUT = 100;
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etCommand = findViewById(R.id.etCommand);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        btnPredict = findViewById(R.id.btnPredict);
        tvResult = findViewById(R.id.tvResult);
        btnSpeak = findViewById(R.id.btnSpeak);
        etCommand = findViewById(R.id.etCommand);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        btnPredict = findViewById(R.id.btnPredict);
        tvResult = findViewById(R.id.tvResult);

        String[] languages = {"TR", "EN", "AR"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                languages
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        apiService = RetrofitClient.getClient().create(ApiService.class);

        btnPredict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPredictionRequest();
            }
        });

        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAudioPermissionAndStartSpeech();
            }
        });
    }

    private void checkAudioPermissionAndStartSpeech() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQUEST_CODE_RECORD_AUDIO_PERMISSION
            );
        } else {
            startSpeechToText();
        }
    }

    private void startSpeechToText() {
        android.content.Intent intent = new android.content.Intent(
                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        String selectedLanguage = spinnerLanguage.getSelectedItem().toString();

        if (selectedLanguage.equals("TR")) {
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        } else if (selectedLanguage.equals("EN")) {
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        } else if (selectedLanguage.equals("AR")) {
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar");
        }

        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Komutu söyle");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition desteklenmiyor: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startSpeechToText();
            } else {
                Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> results =
                    data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etCommand.setText(spokenText);

                sendPredictionRequest();
            }
        }
    }

    private void sendPredictionRequest() {
        String text = etCommand.getText().toString().trim();
        String language = spinnerLanguage.getSelectedItem().toString();

        if (text.isEmpty()) {
            etCommand.setError("Komut boş olamaz");
            return;
        }

        tvResult.setText("İstek gönderiliyor...");

        PredictRequest request = new PredictRequest(text, language);

        apiService.predict(request).enqueue(new Callback<PredictResponse>() {
            @Override
            public void onResponse(Call<PredictResponse> call, Response<PredictResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PredictResponse result = response.body();

                    String output =
                            "Input: " + result.getInput() + "\n\n" +
                                    "Predicted Label: " + result.getPredicted_label() + "\n" +
                                    "Confidence: " + result.getConfidence() + "\n" +
                                    "Accepted: " + result.isAccepted() + "\n" +
                                    "Temperature: " + result.getTemperature();

                    tvResult.setText(output);
                } else {
                    tvResult.setText("API hata döndü. Kod: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<PredictResponse> call, Throwable t) {
                tvResult.setText("Bağlantı hatası: " + t.getMessage());
                Toast.makeText(MainActivity.this, "Sunucuya ulaşılamadı", Toast.LENGTH_SHORT).show();
            }
        });
    }
}