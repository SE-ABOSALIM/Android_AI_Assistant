package com.example.anroidaiassistant.ui.screens;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.api.ApiService;
import com.example.anroidaiassistant.api.RetrofitClient;
import com.example.anroidaiassistant.api.dto.CommandHistoryItem;
import com.example.anroidaiassistant.api.dto.CommandHistoryMutationResponse;
import com.example.anroidaiassistant.api.dto.CommandHistoryResponse;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.session.AssistantSession;
import com.example.anroidaiassistant.util.DeviceIdentity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class HistoryFragment extends Fragment {
    private static final String TAG = "HistoryFragment";
    private static final String APP_MATCH_SCORE_PARAMETER = "app_match_score";
    private static final int INITIAL_LIMIT = 20;
    private static final int LOAD_MORE_LIMIT = 30;

    private ApiService apiService;
    private LinearLayout historyList;
    private TextView successfulCountView;
    private TextView failedCountView;
    private TextView emptyTextView;
    private Button loadMoreButton;
    private EditText searchInput;
    private Call<CommandHistoryResponse> historyCall;
    private final List<CommandHistoryItem> loadedItems = new ArrayList<>();
    private boolean hasMore;
    private boolean isLoading;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setLayoutDirection(isRtl()
                ? View.LAYOUT_DIRECTION_RTL
                : View.LAYOUT_DIRECTION_LTR);
        apiService = RetrofitClient.getClient().create(ApiService.class);
        historyList = view.findViewById(R.id.historyList);
        successfulCountView = view.findViewById(R.id.historySuccessfulCount);
        failedCountView = view.findViewById(R.id.historyFailedCount);
        emptyTextView = view.findViewById(R.id.historyEmptyText);
        loadMoreButton = view.findViewById(R.id.historyLoadMore);
        searchInput = view.findViewById(R.id.historySearchInput);

        loadMoreButton.setOnClickListener(v -> loadHistory(false));
        view.findViewById(R.id.historyClearAll).setOnClickListener(v -> confirmClearHistory());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadHistory(true);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (historyList != null) {
            loadHistory(true);
        }
    }

    @Override
    public void onDestroyView() {
        if (historyCall != null) {
            historyCall.cancel();
            historyCall = null;
        }

        historyList = null;
        successfulCountView = null;
        failedCountView = null;
        emptyTextView = null;
        loadMoreButton = null;
        searchInput = null;
        super.onDestroyView();
    }

    private void loadHistory(boolean reset) {
        if (isLoading || apiService == null) {
            return;
        }

        if (reset) {
            loadedItems.clear();
            if (historyList != null) {
                historyList.removeAllViews();
            }
            hasMore = false;
        }

        isLoading = true;
        updateLoadMoreState();
        int limit = loadedItems.isEmpty() ? INITIAL_LIMIT : LOAD_MORE_LIMIT;
        int offset = loadedItems.size();
        historyCall = apiService.getCommandHistory(
                AssistantSession.getSessionId(),
                DeviceIdentity.getDeviceId(requireContext()),
                limit,
                offset,
                cleanQuery()
        );
        historyCall.enqueue(new Callback<CommandHistoryResponse>() {
            @Override
            public void onResponse(Call<CommandHistoryResponse> call, Response<CommandHistoryResponse> response) {
                if (!isAdded() || call.isCanceled()) {
                    return;
                }

                isLoading = false;
                historyCall = null;
                if (!response.isSuccessful() || response.body() == null) {
                    showError(getString(R.string.history_load_error));
                    updateLoadMoreState();
                    return;
                }

                applyHistoryResponse(response.body());
            }

            @Override
            public void onFailure(Call<CommandHistoryResponse> call, Throwable t) {
                if (!isAdded() || call.isCanceled()) {
                    return;
                }

                isLoading = false;
                historyCall = null;
                Log.e(TAG, "History request failed", t);
                showError(getString(R.string.backend_unavailable));
                updateLoadMoreState();
            }
        });
    }

    private void applyHistoryResponse(CommandHistoryResponse response) {
        successfulCountView.setText(String.valueOf(response.getSuccessfulCount()));
        failedCountView.setText(String.valueOf(response.getFailedCount()));
        hasMore = response.hasMore();

        for (CommandHistoryItem item : response.getItems()) {
            loadedItems.add(item);
            historyList.addView(createHistoryRow(item));
        }

        emptyTextView.setVisibility(loadedItems.isEmpty() ? View.VISIBLE : View.GONE);
        updateLoadMoreState();
    }

    private View createHistoryRow(CommandHistoryItem item) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setLayoutDirection(isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        FrameLayout statusChip = new FrameLayout(requireContext());
        statusChip.setBackgroundResource(item.isAccepted()
                ? R.drawable.history_success_icon_background
                : R.drawable.history_failed_icon_background);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        chipParams.topMargin = dp(2);
        card.addView(statusChip, chipParams);

        ImageView statusIcon = new ImageView(requireContext());
        statusIcon.setImageResource(item.isAccepted()
                ? R.drawable.ic_history_check
                : R.drawable.ic_history_close);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        statusChip.addView(statusIcon, iconParams);

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginStart(dp(12));
        card.addView(textColumn, textParams);

        TextView title = new TextView(requireContext());
        title.setText(nonEmpty(item.getText(), getString(R.string.history_command_fallback)));
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_text_primary));
        title.setTextSize(16);
        title.setGravity(isRtl() ? Gravity.RIGHT : Gravity.LEFT);
        title.setTextDirection(isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(title);

        TextView detail = new TextView(requireContext());
        detail.setText(historyDetail(item));
        detail.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_text_secondary));
        detail.setTextSize(13);
        detail.setGravity(isRtl() ? Gravity.RIGHT : Gravity.LEFT);
        detail.setTextDirection(isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        textColumn.addView(detail, detailParams);

        TextView time = new TextView(requireContext());
        time.setText(formatCreatedAt(item.getCreatedAt()));
        time.setTextColor(ContextCompat.getColor(requireContext(), R.color.bottom_nav_inactive));
        time.setTextSize(12);
        time.setGravity(isRtl() ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.topMargin = dp(5);
        textColumn.addView(time, timeParams);

        ImageView deleteButton = new ImageView(requireContext());
        deleteButton.setImageResource(R.drawable.ic_history_delete);
        deleteButton.setPadding(dp(6), dp(6), dp(6), dp(6));
        deleteButton.setOnClickListener(v -> deleteHistoryItem(item));
        card.addView(deleteButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        return card;
    }

    private void deleteHistoryItem(CommandHistoryItem item) {
        if (item == null || !hasText(item.getId())) {
            return;
        }

        apiService.deleteCommandHistoryItem(
                item.getId(),
                AssistantSession.getSessionId(),
                DeviceIdentity.getDeviceId(requireContext())
        ).enqueue(new Callback<CommandHistoryMutationResponse>() {
            @Override
            public void onResponse(
                    Call<CommandHistoryMutationResponse> call,
                    Response<CommandHistoryMutationResponse> response
            ) {
                if (isAdded()) {
                    loadHistory(true);
                }
            }

            @Override
            public void onFailure(Call<CommandHistoryMutationResponse> call, Throwable t) {
                if (isAdded()) {
                    showError(getString(R.string.history_delete_error));
                }
            }
        });
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setPositiveButton(R.string.history_clear_confirm, (dialog, which) -> clearHistory())
                .setNegativeButton(R.string.common_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void clearHistory() {
        apiService.clearCommandHistory(
                AssistantSession.getSessionId(),
                DeviceIdentity.getDeviceId(requireContext())
        ).enqueue(new Callback<CommandHistoryMutationResponse>() {
            @Override
            public void onResponse(
                    Call<CommandHistoryMutationResponse> call,
                    Response<CommandHistoryMutationResponse> response
            ) {
                if (isAdded()) {
                    loadHistory(true);
                }
            }

            @Override
            public void onFailure(Call<CommandHistoryMutationResponse> call, Throwable t) {
                if (isAdded()) {
                    showError(getString(R.string.history_clear_error));
                }
            }
        });
    }

    private void updateLoadMoreState() {
        if (loadMoreButton == null) {
            return;
        }

        loadMoreButton.setEnabled(!isLoading);
        loadMoreButton.setText(isLoading ? R.string.history_loading : R.string.history_load_more);
        loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
    }

    private String historyDetail(CommandHistoryItem item) {
        if (!item.isAccepted()) {
            return getString(
                    R.string.history_failed_detail,
                    nonEmpty(item.getErrorCode(), getString(R.string.history_command_not_accepted))
            );
        }

        String intent = formatIntent(item.getIntent());
        String parameterSummary = parameterSummary(item.getParameters());
        if (hasText(parameterSummary)) {
            return intent + " - " + parameterSummary;
        }
        return getString(R.string.history_executed_detail, intent);
    }

    private String parameterSummary(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() == null || APP_MATCH_SCORE_PARAMETER.equals(entry.getKey())) {
                continue;
            }
            parts.add(entry.getKey() + ": " + entry.getValue());
            if (parts.size() >= 2) {
                break;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private String formatIntent(String intent) {
        if (!hasText(intent)) {
            return getString(R.string.history_command_detail_fallback);
        }

        String[] parts = intent.toLowerCase(Locale.US).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? getString(R.string.history_command_detail_fallback) : builder.toString();
    }

    private String formatCreatedAt(String createdAt) {
        if (!hasText(createdAt)) {
            return "";
        }

        String text = createdAt.replace('T', ' ');
        int plusIndex = text.indexOf('+');
        if (plusIndex > 0) {
            text = text.substring(0, plusIndex);
        }
        int dotIndex = text.indexOf('.');
        if (dotIndex > 0) {
            text = text.substring(0, dotIndex);
        }
        return text.length() > 16 ? text.substring(0, 16) : text;
    }

    private String cleanQuery() {
        if (searchInput == null) {
            return null;
        }

        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        return query.isEmpty() ? null : query;
    }

    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private String nonEmpty(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isRtl() {
        return AssistantSettings.isRtl(requireContext());
    }
}
