package com.example.anroidaiassistant.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.CustomCommandItem;
import com.example.anroidaiassistant.api.dto.CustomCommandListResponse;
import com.example.anroidaiassistant.api.dto.CustomCommandMutationRequest;
import com.example.anroidaiassistant.api.dto.CustomCommandMutationResponse;
import com.example.anroidaiassistant.api.dto.CustomCommandStep;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.util.DeviceIdentity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class CustomCommandsFragment extends Fragment {
    private LinearLayout root;
    private LinearLayout listContainer;
    private LinearLayout emptyState;
    private TextView countView;
    private ApiService apiService;
    private String deviceId;
    private String language;
    private final List<CustomCommand> commands = new ArrayList<>();
    private final List<Call<?>> activeCalls = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_custom_commands, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view.findViewById(R.id.customCommandsRoot);
        listContainer = view.findViewById(R.id.customCommandsList);
        emptyState = view.findViewById(R.id.customCommandsEmptyState);
        countView = view.findViewById(R.id.customCommandsCount);
        apiService = RetrofitClient.getClient().create(ApiService.class);
        deviceId = DeviceIdentity.getDeviceId(requireContext());
        language = AssistantSettings.getLanguage(requireContext());

        applyPageDirection(view);
        view.findViewById(R.id.customCommandsAddButton)
                .setOnClickListener(v -> showCommandEditor(null));

        renderCommands();
        loadCommands();
    }

    @Override
    public void onDestroyView() {
        for (Call<?> call : new ArrayList<>(activeCalls)) {
            call.cancel();
        }
        activeCalls.clear();
        root = null;
        listContainer = null;
        emptyState = null;
        countView = null;
        super.onDestroyView();
    }

    private void applyPageDirection(View view) {
        boolean rtl = isRtl();
        view.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        int gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
        applyTextDirection(view.findViewById(R.id.customCommandsTitle), gravity);
        applyTextDirection(view.findViewById(R.id.customCommandsSubtitle), gravity);
        applyTextDirection(view.findViewById(R.id.customCommandsCount), gravity);
        applyTextDirection(view.findViewById(R.id.customCommandsSummaryLabel), gravity);
        applyTextDirection(view.findViewById(R.id.customCommandsEmptyTitle), Gravity.CENTER);
        applyTextDirection(view.findViewById(R.id.customCommandsEmptyHint), Gravity.CENTER);
    }

    private void loadCommands() {
        if (!hasText(deviceId)) {
            showToast(R.string.custom_commands_load_error);
            return;
        }

        Call<CustomCommandListResponse> call = apiService.getCustomCommands(deviceId, language);
        track(call);
        call.enqueue(new Callback<CustomCommandListResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<CustomCommandListResponse> call,
                    @NonNull Response<CustomCommandListResponse> response
            ) {
                untrack(call);
                if (!isScreenActive()) {
                    return;
                }

                commands.clear();
                CustomCommandListResponse body = response.body();
                if (response.isSuccessful() && body != null) {
                    for (CustomCommandItem item : body.getItems()) {
                        CustomCommand command = fromItem(item);
                        if (command != null) {
                            commands.add(command);
                        }
                    }
                } else {
                    showToast(R.string.custom_commands_load_error);
                }
                renderCommands();
            }

            @Override
            public void onFailure(@NonNull Call<CustomCommandListResponse> call, @NonNull Throwable throwable) {
                untrack(call);
                if (!call.isCanceled() && isScreenActive()) {
                    showToast(R.string.custom_commands_load_error);
                    renderCommands();
                }
            }
        });
    }

    private void renderCommands() {
        if (listContainer == null || countView == null || emptyState == null) {
            return;
        }

        listContainer.removeAllViews();
        countView.setText(getString(R.string.custom_commands_count, commands.size()));
        emptyState.setVisibility(commands.isEmpty() ? View.VISIBLE : View.GONE);

        for (CustomCommand command : commands) {
            listContainer.addView(commandCard(command));
        }
    }

    private View commandCard(CustomCommand command) {
        Context context = requireContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setLayoutDirection(isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(card.getLayoutDirection());

        TextView name = text(context, command.name, 18, R.color.app_text_primary, true);
        header.addView(name, weightedWrapParams());

        TextView edit = actionText(context, getString(R.string.custom_commands_edit));
        edit.setOnClickListener(v -> showCommandEditor(command));
        header.addView(edit);

        TextView delete = actionText(context, getString(R.string.custom_commands_delete));
        delete.setTextColor(ContextCompat.getColor(context, R.color.app_danger));
        delete.setOnClickListener(v -> deleteCommand(command));
        header.addView(delete);
        card.addView(header);

        TextView phrase = text(
                context,
                getString(R.string.custom_commands_phrase_format, command.name),
                14,
                R.color.app_primary,
                true
        );
        phrase.setBackgroundResource(R.drawable.custom_command_soft_background);
        phrase.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams phraseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        phraseParams.topMargin = dp(10);
        card.addView(phrase, phraseParams);

        TextView stepCount = text(
                context,
                getString(R.string.custom_commands_step_count, command.steps.size()),
                13,
                R.color.app_text_secondary,
                false
        );
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        countParams.topMargin = dp(10);
        card.addView(stepCount, countParams);

        for (int i = 0; i < command.steps.size(); i++) {
            CustomStep step = command.steps.get(i);
            TextView stepView = text(
                    context,
                    getString(
                            R.string.custom_step_display,
                            i + 1,
                            labelForIntent(step.intent),
                            displayValueForStep(step)
                    ),
                    14,
                    R.color.app_text_secondary,
                    false
            );
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            stepParams.topMargin = dp(5);
            card.addView(stepView, stepParams);
        }

        return card;
    }

    private void showCommandEditor(@Nullable CustomCommand existingCommand) {
        Context context = requireContext();
        boolean editing = existingCommand != null;
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout editor = new LinearLayout(context);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(20), dp(18), dp(20), dp(24));
        editor.setLayoutDirection(isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scrollView.addView(editor);

        TextView title = text(
                context,
                editing ? getString(R.string.custom_commands_update) : getString(R.string.custom_commands_save),
                20,
                R.color.app_text_primary,
                true
        );
        editor.addView(title);

        TextView nameLabel = text(context, getString(R.string.custom_commands_name_label), 13, R.color.app_text_secondary, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dp(16);
        editor.addView(nameLabel, labelParams);

        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        nameInput.setHintTextColor(ContextCompat.getColor(context, R.color.bottom_nav_inactive));
        nameInput.setHint(R.string.custom_commands_name_hint);
        nameInput.setText(editing ? existingCommand.name : "");
        nameInput.setTextDirection(isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        nameInput.setGravity((isRtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        editor.addView(nameInput, matchWrapParams());

        TextView stepsLabel = text(context, getString(R.string.custom_commands_steps_label), 13, R.color.app_text_secondary, true);
        LinearLayout.LayoutParams stepsLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stepsLabelParams.topMargin = dp(16);
        editor.addView(stepsLabel, stepsLabelParams);

        LinearLayout stepsContainer = new LinearLayout(context);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        editor.addView(stepsContainer, matchWrapParams());

        List<StepRow> stepRows = new ArrayList<>();
        if (editing) {
            for (CustomStep step : existingCommand.steps) {
                addStepRow(stepsContainer, stepRows, step);
            }
        } else {
            addStepRow(stepsContainer, stepRows, null);
        }

        Button addStep = new Button(context);
        addStep.setText(R.string.custom_commands_add_step);
        addStep.setAllCaps(false);
        addStep.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
        addStep.setBackgroundResource(R.drawable.custom_command_soft_background);
        addStep.setOnClickListener(v -> addStepRow(stepsContainer, stepRows, null));
        LinearLayout.LayoutParams addStepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        addStepParams.topMargin = dp(12);
        editor.addView(addStep, addStepParams);

        Button saveButton = new Button(context);
        saveButton.setText(editing ? R.string.custom_commands_update : R.string.custom_commands_save);
        saveButton.setAllCaps(false);
        saveButton.setTextColor(ContextCompat.getColor(context, R.color.white));
        saveButton.setBackgroundResource(R.drawable.home_turn_on_button_background);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        saveParams.topMargin = dp(14);
        editor.addView(saveButton, saveParams);

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(context, R.string.custom_commands_validation_name, Toast.LENGTH_SHORT).show();
                return;
            }

            List<CustomStep> steps = collectSteps(stepRows);
            if (steps.isEmpty()) {
                Toast.makeText(context, R.string.custom_commands_validation_step, Toast.LENGTH_SHORT).show();
                return;
            }

            CustomCommand command = editing ? existingCommand : new CustomCommand();
            command.id = editing ? existingCommand.id : null;
            command.name = name;
            command.steps.clear();
            command.steps.addAll(steps);
            saveCommand(command, editing, dialog);
        });

        dialog.setContentView(scrollView);
        dialog.show();
    }

    private void addStepRow(LinearLayout container, List<StepRow> stepRows, @Nullable CustomStep existingStep) {
        Context context = requireContext();
        StepOption[] options = stepOptions();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.setLayoutDirection(isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutDirection(card.getLayoutDirection());

        Spinner spinner = new Spinner(context);
        List<String> labels = new ArrayList<>();
        for (StepOption option : options) {
            labels.add(option.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(optionIndexForIntent(existingStep == null ? null : existingStep.intent));
        topRow.addView(spinner, weightedWrapParams());

        TextView delete = actionText(context, getString(R.string.custom_commands_delete));
        delete.setTextColor(ContextCompat.getColor(context, R.color.app_danger));
        topRow.addView(delete);
        card.addView(topRow);

        EditText valueInput = new EditText(context);
        valueInput.setSingleLine(true);
        valueInput.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary));
        valueInput.setHintTextColor(ContextCompat.getColor(context, R.color.bottom_nav_inactive));
        valueInput.setText(existingStep == null ? "" : existingStep.value);
        valueInput.setTextDirection(isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        valueInput.setGravity((isRtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        valueInput.setHint(options[spinner.getSelectedItemPosition()].hint);
        configureValueInputForIntent(valueInput, options[spinner.getSelectedItemPosition()].intent);
        card.addView(valueInput, matchWrapParams());

        StepRow row = new StepRow(spinner, valueInput);
        stepRows.add(row);
        container.addView(card, cardParams);

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                valueInput.setHint(options[position].hint);
                configureValueInputForIntent(valueInput, options[position].intent);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        delete.setOnClickListener(v -> {
            stepRows.remove(row);
            container.removeView(card);
        });
    }

    private List<CustomStep> collectSteps(List<StepRow> rows) {
        StepOption[] options = stepOptions();
        List<CustomStep> steps = new ArrayList<>();
        for (StepRow row : rows) {
            int selected = Math.max(0, row.spinner.getSelectedItemPosition());
            String intent = options[selected].intent;
            String value = row.valueInput.getText().toString().trim();
            if (TextUtils.isEmpty(value) && !"SHOW_LABELS".equals(intent)) {
                continue;
            }
            if ("WAIT".equals(intent) && !isValidSeconds(value)) {
                Toast.makeText(requireContext(), R.string.custom_commands_validation_wait_seconds, Toast.LENGTH_SHORT).show();
                return new ArrayList<>();
            }

            CustomStep step = new CustomStep();
            step.intent = intent;
            step.value = value;
            steps.add(step);
        }
        return steps;
    }

    private void saveCommand(CustomCommand command, boolean editing, BottomSheetDialog dialog) {
        if (!hasText(deviceId)) {
            showToast(R.string.custom_commands_save_error);
            return;
        }

        CustomCommandMutationRequest request = new CustomCommandMutationRequest(
                deviceId,
                language,
                command.name,
                toDtoSteps(command.steps)
        );
        Call<CustomCommandMutationResponse> call = editing
                ? apiService.updateCustomCommand(command.id, request)
                : apiService.createCustomCommand(request);
        track(call);
        call.enqueue(new Callback<CustomCommandMutationResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<CustomCommandMutationResponse> call,
                    @NonNull Response<CustomCommandMutationResponse> response
            ) {
                untrack(call);
                if (!isScreenActive()) {
                    return;
                }

                CustomCommandMutationResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isAccepted()) {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.custom_commands_saved, Toast.LENGTH_SHORT).show();
                    loadCommands();
                    return;
                }
                showToast(R.string.custom_commands_save_error);
            }

            @Override
            public void onFailure(@NonNull Call<CustomCommandMutationResponse> call, @NonNull Throwable throwable) {
                untrack(call);
                if (!call.isCanceled() && isScreenActive()) {
                    showToast(R.string.custom_commands_save_error);
                }
            }
        });
    }

    private void deleteCommand(CustomCommand command) {
        if (!hasText(deviceId) || !hasText(command.id)) {
            showToast(R.string.custom_commands_delete_error);
            return;
        }

        Call<CustomCommandMutationResponse> call = apiService.deleteCustomCommand(command.id, deviceId);
        track(call);
        call.enqueue(new Callback<CustomCommandMutationResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<CustomCommandMutationResponse> call,
                    @NonNull Response<CustomCommandMutationResponse> response
            ) {
                untrack(call);
                if (!isScreenActive()) {
                    return;
                }

                CustomCommandMutationResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isAccepted()) {
                    commands.remove(command);
                    renderCommands();
                    Toast.makeText(requireContext(), R.string.custom_commands_deleted, Toast.LENGTH_SHORT).show();
                    return;
                }
                showToast(R.string.custom_commands_delete_error);
            }

            @Override
            public void onFailure(@NonNull Call<CustomCommandMutationResponse> call, @NonNull Throwable throwable) {
                untrack(call);
                if (!call.isCanceled() && isScreenActive()) {
                    showToast(R.string.custom_commands_delete_error);
                }
            }
        });
    }

    private CustomCommand fromItem(CustomCommandItem item) {
        if (item == null || !hasText(item.getName())) {
            return null;
        }

        CustomCommand command = new CustomCommand();
        command.id = item.getId();
        command.name = item.getName();
        for (CustomCommandStep dtoStep : item.getSteps()) {
            CustomStep step = fromDtoStep(dtoStep);
            if (step != null) {
                command.steps.add(step);
            }
        }
        return command.steps.isEmpty() ? null : command;
    }

    private CustomStep fromDtoStep(CustomCommandStep dtoStep) {
        if (dtoStep == null || !hasText(dtoStep.getIntent())) {
            return null;
        }

        String intent = dtoStep.getIntent().trim().toUpperCase();
        CustomStep step = new CustomStep();
        step.intent = intent;
        step.value = valueFromParameters(intent, dtoStep.getParameters());
        return step;
    }

    private List<CustomCommandStep> toDtoSteps(List<CustomStep> steps) {
        List<CustomCommandStep> result = new ArrayList<>();
        for (CustomStep step : steps) {
            Map<String, Object> parameters = parametersForStep(step);
            CustomCommandStep dtoStep = new CustomCommandStep(step.intent, parameters);
            result.add(dtoStep);
        }
        return result;
    }

    private Map<String, Object> parametersForStep(CustomStep step) {
        Map<String, Object> parameters = new HashMap<>();
        if (step == null || !hasText(step.intent)) {
            return parameters;
        }

        if ("OPEN_APP".equals(step.intent)) {
            parameters.put("app_name", step.value);
        } else if ("SEARCH_QUERY".equals(step.intent)) {
            parameters.put("query", step.value);
        } else if ("CLICK_ITEM".equals(step.intent)) {
            parameters.put("target_text", step.value);
        } else if ("SHOW_LABELS".equals(step.intent) && hasText(step.value)) {
            parameters.put("label_number", step.value);
        } else if ("WAIT".equals(step.intent)) {
            parameters.put("duration_ms", secondsToMillis(step.value));
        }
        return parameters;
    }

    private String valueFromParameters(String intent, Map<String, Object> parameters) {
        if ("OPEN_APP".equals(intent)) {
            return stringParam(parameters, "app_name");
        }
        if ("SEARCH_QUERY".equals(intent)) {
            return stringParam(parameters, "query");
        }
        if ("CLICK_ITEM".equals(intent)) {
            return stringParam(parameters, "target_text");
        }
        if ("SHOW_LABELS".equals(intent)) {
            return stringParam(parameters, "label_number");
        }
        if ("WAIT".equals(intent)) {
            return millisToSecondsText(stringParam(parameters, "duration_ms"));
        }
        return "";
    }

    private String stringParam(Map<String, Object> parameters, String key) {
        if (parameters == null || parameters.get(key) == null) {
            return "";
        }
        return String.valueOf(parameters.get(key)).trim();
    }

    private String displayValueForStep(CustomStep step) {
        if (step == null || !hasText(step.value)) {
            return getString(R.string.custom_step_show_only);
        }
        if ("WAIT".equals(step.intent)) {
            return getString(R.string.custom_step_wait_seconds_format, step.value);
        }
        return step.value;
    }

    private StepOption[] stepOptions() {
        return new StepOption[]{
                new StepOption("OPEN_APP", getString(R.string.custom_step_open_app), getString(R.string.custom_step_hint_app_name)),
                new StepOption("SEARCH_QUERY", getString(R.string.custom_step_search_query), getString(R.string.custom_step_hint_query)),
                new StepOption("CLICK_ITEM", getString(R.string.custom_step_click_item), getString(R.string.custom_step_hint_target)),
                new StepOption("SHOW_LABELS", getString(R.string.custom_step_show_labels), getString(R.string.custom_step_hint_label_number)),
                new StepOption("WAIT", getString(R.string.custom_step_wait), getString(R.string.custom_step_hint_wait_seconds))
        };
    }

    private int optionIndexForIntent(@Nullable String intent) {
        StepOption[] options = stepOptions();
        for (int i = 0; i < options.length; i++) {
            if (options[i].intent.equals(intent)) {
                return i;
            }
        }
        return 0;
    }

    private String labelForIntent(String intent) {
        StepOption[] options = stepOptions();
        for (StepOption option : options) {
            if (option.intent.equals(intent)) {
                return option.label;
            }
        }
        return intent;
    }

    private void configureValueInputForIntent(EditText valueInput, String intent) {
        if ("WAIT".equals(intent)) {
            valueInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
    }

    private int secondsToMillis(String secondsText) {
        Double seconds = parseSeconds(secondsText);
        if (seconds == null) {
            return 0;
        }
        return (int) Math.max(0, Math.round(seconds * 1000.0d));
    }

    private String millisToSecondsText(String millisText) {
        if (!hasText(millisText)) {
            return "";
        }
        try {
            double millis = Double.parseDouble(millisText.trim());
            return formatSeconds(millis / 1000.0d);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private boolean isValidSeconds(String value) {
        return parseSeconds(value) != null;
    }

    private Double parseSeconds(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(normalizeSecondsText(value));
            if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
                return null;
            }
            return seconds;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeSecondsText(String value) {
        StringBuilder result = new StringBuilder();
        boolean hasDecimalSeparator = false;

        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            int digit = Character.getNumericValue(c);

            if (digit >= 0 && digit <= 9) {
                result.append(digit);
            } else if ((c == '.' || c == ',' || c == '\u066B') && !hasDecimalSeparator) {
                result.append('.');
                hasDecimalSeparator = true;
            } else if (c == '\u066C' || Character.isWhitespace(c)) {
                // Ignore Arabic thousands separator and accidental spaces.
            } else {
                result.append(c);
            }
        }

        return result.toString().trim();
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.000001d) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format(Locale.US, "%.2f", seconds).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private TextView text(Context context, String value, int sizeSp, int colorRes, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(ContextCompat.getColor(context, colorRes));
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        applyTextDirection(text, isRtl() ? Gravity.RIGHT : Gravity.LEFT);
        return text;
    }

    private TextView actionText(Context context, String value) {
        TextView text = text(context, value, 13, R.color.app_primary, true);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(10), dp(8), dp(2), dp(8));
        return text;
    }

    private void applyTextDirection(TextView textView, int gravity) {
        textView.setGravity(gravity);
        textView.setTextDirection(isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    private LinearLayout.LayoutParams weightedWrapParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void track(Call<?> call) {
        activeCalls.add(call);
    }

    private void untrack(Call<?> call) {
        activeCalls.remove(call);
    }

    private boolean isScreenActive() {
        return isAdded() && listContainer != null;
    }

    private void showToast(int stringRes) {
        if (isAdded()) {
            Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isRtl() {
        return isAdded() && AssistantSettings.isRtl(requireContext());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CustomCommand {
        private String id;
        private String name;
        private final List<CustomStep> steps = new ArrayList<>();
    }

    private static final class CustomStep {
        private String intent;
        private String value;
    }

    private static final class StepOption {
        private final String intent;
        private final String label;
        private final String hint;

        private StepOption(String intent, String label, String hint) {
            this.intent = intent;
            this.label = label;
            this.hint = hint;
        }
    }

    private static final class StepRow {
        private final Spinner spinner;
        private final EditText valueInput;

        private StepRow(Spinner spinner, EditText valueInput) {
            this.spinner = spinner;
            this.valueInput = valueInput;
        }
    }
}
