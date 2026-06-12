package com.example.anroidaiassistant.ui.screens;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.api.dto.PredictResponse;

public final class HomeFragment extends Fragment {
    private Button btnSpeak;
    private TextView tvResult;
    private TextView tvHomeHelper;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnSpeak = view.findViewById(R.id.btnSpeak);
        tvResult = view.findViewById(R.id.tvResult);
        tvHomeHelper = view.findViewById(R.id.tvHomeHelper);

        btnSpeak.setOnClickListener(v -> {
            MainActivity activity = mainActivity();
            if (activity != null) {
                activity.toggleListeningFromHome();
            }
        });

        MainActivity activity = mainActivity();
        if (activity != null) {
            activity.attachHomeFragment(this);
        }
    }

    @Override
    public void onDestroyView() {
        MainActivity activity = mainActivity();
        if (activity != null) {
            activity.detachHomeFragment(this);
        }

        btnSpeak = null;
        tvResult = null;
        tvHomeHelper = null;

        super.onDestroyView();
    }

    public void setListeningState(boolean listening) {
        if (btnSpeak == null || tvResult == null || tvHomeHelper == null) {
            return;
        }

        btnSpeak.setText(listening ? "Turn Off" : "Turn On");
        btnSpeak.setBackgroundResource(listening
                ? R.drawable.home_turn_off_button_background
                : R.drawable.home_turn_on_button_background);
        tvResult.setText(listening ? "Assistant is Active" : "Assistant is Inactive");
        tvResult.setTextColor(listening
                ? Color.parseColor("#111827")
                : Color.parseColor("#111827"));
        tvHomeHelper.setText(listening
                ? "Listening for voice commands"
                : "Enable to start using voice commands");
    }

    public void setSpeakButtonEnabled(boolean enabled) {
        if (btnSpeak != null) {
            btnSpeak.setEnabled(enabled);
            btnSpeak.setAlpha(enabled ? 1f : 0.55f);
        }
    }

    public void setBackendState(String text) {
        // Home intentionally keeps backend details out of the primary UI.
    }

    public void setStatusText(String text) {
        if (tvResult != null) {
            tvResult.setText(text);
        }
    }

    public void showPredictionResult(PredictResponse response) {
        if (response == null) {
            setStatusText("No response from backend");
            return;
        }

        String responseInfo = "intent: " + response.getIntent() + "\n"
                + "language: " + response.getLanguage() + "\n"
                + "confidence: " + response.getConfidence() + "\n"
                + "accepted: " + response.isAccepted() + "\n"
                + "parameters: " + response.getParameters() + "\n"
                + "missing_slots: " + response.getMissingSlots() + "\n"
                + "error_code: " + response.getErrorCode() + "\n"
                + "error_message: " + response.getErrorMessage();
        setStatusText(responseInfo);
    }

    private MainActivity mainActivity() {
        return getActivity() instanceof MainActivity
                ? (MainActivity) getActivity()
                : null;
    }
}
