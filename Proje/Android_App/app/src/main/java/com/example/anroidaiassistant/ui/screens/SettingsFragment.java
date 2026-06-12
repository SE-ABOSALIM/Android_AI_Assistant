package com.example.anroidaiassistant.ui.screens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class SettingsFragment extends Fragment {
    private TextView languageValueView;
    private TextView themeValueView;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        languageValueView = view.findViewById(R.id.settingsLanguageValue);
        themeValueView = view.findViewById(R.id.settingsThemeValue);

        view.findViewById(R.id.settingsLanguageCard).setOnClickListener(v -> showLanguageSheet());
        view.findViewById(R.id.settingsThemeCard).setOnClickListener(v -> showThemeSheet());
        updateDisplayedValues();
    }

    private void showLanguageSheet() {
        String language = AssistantSettings.getLanguage(requireContext());
        showSelectionSheet(
                "Language",
                new SelectionOption[]{
                        new SelectionOption(AssistantSettings.LANGUAGE_EN, "English"),
                        new SelectionOption(AssistantSettings.LANGUAGE_TR, "Türkçe"),
                        new SelectionOption(AssistantSettings.LANGUAGE_AR, "العربية")
                },
                language,
                selectedLanguage -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateSelectedLanguage(selectedLanguage);
                    } else {
                        AssistantSettings.setLanguage(requireContext(), selectedLanguage);
                    }
                    updateDisplayedValues();
                }
        );
    }

    private void showThemeSheet() {
        String theme = AssistantSettings.getTheme(requireContext());
        showSelectionSheet(
                "Theme",
                new SelectionOption[]{
                        new SelectionOption(AssistantSettings.THEME_SYSTEM, "System default"),
                        new SelectionOption(AssistantSettings.THEME_LIGHT, "Light"),
                        new SelectionOption(AssistantSettings.THEME_DARK, "Dark")
                },
                theme,
                selectedTheme -> {
                    AssistantSettings.setTheme(requireContext(), selectedTheme);
                    updateDisplayedValues();
                }
        );
    }

    private void updateDisplayedValues() {
        languageValueView.setText(AssistantSettings.languageLabel(AssistantSettings.getLanguage(requireContext())));
        themeValueView.setText(AssistantSettings.themeLabel(AssistantSettings.getTheme(requireContext())));
    }

    private void showSelectionSheet(
            String title,
            SelectionOption[] options,
            String selectedValue,
            SelectionCallback callback
    ) {
        Context context = requireContext();
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(26));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout optionsContainer = new LinearLayout(context);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.topMargin = dp(12);
        root.addView(optionsContainer, containerParams);

        ColorStateList tint = radioTint();
        for (SelectionOption option : options) {
            RadioButton button = new RadioButton(context);
            button.setText(option.label);
            button.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
            button.setTextSize(17);
            button.setMinHeight(dp(54));
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            button.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            button.setTextDirection(View.TEXT_DIRECTION_LTR);
            button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            button.setButtonTintList(tint);
            button.setChecked(option.value.equals(selectedValue));
            button.setPadding(0, 0, 0, 0);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onSelected(option.value);
            });
            optionsContainer.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        dialog.setContentView(root);
        dialog.show();
    }

    private ColorStateList radioTint() {
        int checkedColor = ContextCompat.getColor(requireContext(), R.color.app_primary);
        int uncheckedColor = ContextCompat.getColor(requireContext(), R.color.bottom_nav_inactive);
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedColor, uncheckedColor}
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface SelectionCallback {
        void onSelected(String value);
    }

    private static final class SelectionOption {
        private final String value;
        private final String label;

        private SelectionOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
