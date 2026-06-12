package com.example.anroidaiassistant.ui.screens;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.settings.AssistantSettings;

public final class SettingsFragment extends Fragment {
    private RadioGroup languageGroup;
    private RadioGroup themeGroup;

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
        languageGroup = view.findViewById(R.id.languageGroup);
        themeGroup = view.findViewById(R.id.themeGroup);

        tintRadioButtons(languageGroup);
        tintRadioButtons(themeGroup);
        bindLanguageSettings();
        bindThemeSettings();
    }

    private void bindLanguageSettings() {
        String language = AssistantSettings.getLanguage(requireContext());
        languageGroup.check(languageButtonId(language));
        languageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedLanguage = languageFromButtonId(checkedId);
            AssistantSettings.setLanguage(requireContext(), selectedLanguage);

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateSelectedLanguage(selectedLanguage);
            }
        });
    }

    private void bindThemeSettings() {
        String theme = AssistantSettings.getTheme(requireContext());
        themeGroup.check(themeButtonId(theme));
        themeGroup.setOnCheckedChangeListener((group, checkedId) ->
                AssistantSettings.setTheme(requireContext(), themeFromButtonId(checkedId)));
    }

    private int languageButtonId(String language) {
        if (AssistantSettings.LANGUAGE_EN.equals(language)) {
            return R.id.rbLanguageEnglish;
        }
        if (AssistantSettings.LANGUAGE_AR.equals(language)) {
            return R.id.rbLanguageArabic;
        }
        return R.id.rbLanguageTurkish;
    }

    private String languageFromButtonId(int checkedId) {
        if (checkedId == R.id.rbLanguageEnglish) {
            return AssistantSettings.LANGUAGE_EN;
        }
        if (checkedId == R.id.rbLanguageArabic) {
            return AssistantSettings.LANGUAGE_AR;
        }
        return AssistantSettings.LANGUAGE_TR;
    }

    private int themeButtonId(String theme) {
        if (AssistantSettings.THEME_LIGHT.equals(theme)) {
            return R.id.rbThemeLight;
        }
        if (AssistantSettings.THEME_DARK.equals(theme)) {
            return R.id.rbThemeDark;
        }
        return R.id.rbThemeSystem;
    }

    private String themeFromButtonId(int checkedId) {
        if (checkedId == R.id.rbThemeLight) {
            return AssistantSettings.THEME_LIGHT;
        }
        if (checkedId == R.id.rbThemeDark) {
            return AssistantSettings.THEME_DARK;
        }
        return AssistantSettings.THEME_SYSTEM;
    }

    private void tintRadioButtons(RadioGroup group) {
        int checkedColor = ContextCompat.getColor(requireContext(), R.color.app_primary);
        int uncheckedColor = ContextCompat.getColor(requireContext(), R.color.bottom_nav_inactive);
        ColorStateList tint = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedColor, uncheckedColor}
        );

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof RadioButton) {
                ((RadioButton) child).setButtonTintList(tint);
            }
        }
    }
}
