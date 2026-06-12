package com.example.anroidaiassistant.ui.screens;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.R;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public final class PermissionsFragment extends Fragment {
    private static final int REQUEST_RUNTIME_PERMISSION = 610;

    private LinearLayout container;
    private PermissionGroup activeGroup;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_permissions, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.permission_sections_container);
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RUNTIME_PERMISSION) {
            render();
        }
    }

    private void render() {
        if (container == null) {
            return;
        }

        clearDynamicContent();
        if (activeGroup == null) {
            renderOverview();
        } else {
            renderGroupDetails(activeGroup);
        }
    }

    private void clearDynamicContent() {
        while (container.getChildCount() > 2) {
            container.removeViewAt(2);
        }
    }

    private void renderOverview() {
        PermissionGroup appPermissions = PermissionGroup.appPermissions(runtimePermissions());
        PermissionGroup advancedAccess = PermissionGroup.advancedAccess(advancedPermissions());

        container.addView(createGroupCard(appPermissions));
        container.addView(createGroupCard(advancedAccess));
    }

    private void renderGroupDetails(PermissionGroup group) {
        TextView back = new TextView(requireContext());
        back.setText("< Permissions");
        back.setTextColor(getColor(R.color.app_primary));
        back.setTextSize(15);
        back.setTypeface(back.getTypeface(), android.graphics.Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(0, dp(20), 0, dp(8));
        back.setOnClickListener(view -> {
            activeGroup = null;
            render();
        });
        container.addView(back);

        TextView title = new TextView(requireContext());
        title.setText(group.title);
        title.setTextColor(getColor(R.color.app_text_primary));
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(group.description);
        subtitle.setTextColor(getColor(R.color.app_text_secondary));
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(6), 0, 0);
        container.addView(subtitle, subtitleParams);

        container.addView(createSummaryCard(group));
        for (PermissionItem item : group.items) {
            container.addView(createPermissionRow(item));
        }
    }

    private MaterialCardView createGroupCard(PermissionGroup group) {
        Context context = requireContext();
        int granted = grantedCount(group.items);

        MaterialCardView card = createBaseCard();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            activeGroup = group;
            render();
        });

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(group.title);
        title.setTextColor(getColor(R.color.app_text_primary));
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        textColumn.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(group.description);
        subtitle.setTextColor(getColor(R.color.app_text_secondary));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(5), 0, 0);
        textColumn.addView(subtitle, subtitleParams);

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView count = new TextView(context);
        count.setText(granted + "/" + group.items.size());
        count.setTextColor(getColor(R.color.app_primary));
        count.setTextSize(22);
        count.setGravity(Gravity.CENTER);
        count.setTypeface(count.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(count, new LinearLayout.LayoutParams(dp(62), dp(48)));

        TextView arrow = new TextView(context);
        arrow.setText(">");
        arrow.setTextColor(getColor(R.color.app_text_secondary));
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(48)));

        card.addView(row);
        return card;
    }

    private MaterialCardView createSummaryCard(PermissionGroup group) {
        int granted = grantedCount(group.items);

        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(getColor(R.color.app_primary_soft));
        card.setRadius(dp(8));
        card.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(18), 0, dp(14));
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView count = new TextView(requireContext());
        count.setText(granted + "/" + group.items.size());
        count.setTextColor(getColor(R.color.app_primary));
        count.setTextSize(22);
        count.setTypeface(count.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(count);

        TextView label = new TextView(requireContext());
        label.setText("Permissions Granted");
        label.setTextColor(getColor(R.color.app_text_secondary));
        label.setTextSize(12);
        content.addView(label);

        card.addView(content);
        return card;
    }

    private MaterialCardView createPermissionRow(PermissionItem item) {
        Context context = requireContext();
        boolean granted = item.isGranted(context);

        MaterialCardView card = createBaseCard();
        card.setOnClickListener(view -> requestOrOpen(item));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView icon = new TextView(context);
        icon.setText(item.iconText);
        icon.setTextColor(getColor(R.color.app_primary));
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(icon.getTypeface(), android.graphics.Typeface.BOLD);
        icon.setBackgroundResource(R.drawable.permission_icon_background);
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(item.title);
        title.setTextColor(getColor(R.color.app_text_primary));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        textColumn.addView(title);

        TextView description = new TextView(context);
        description.setText(item.description);
        description.setTextColor(getColor(R.color.app_text_secondary));
        description.setTextSize(12);
        description.setLineSpacing(dp(1), 1f);
        textColumn.addView(description);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMargins(dp(14), 0, dp(10), 0);
        row.addView(textColumn, textParams);

        SwitchCompat switchView = new SwitchCompat(context);
        switchView.setChecked(granted);
        switchView.setFocusable(false);
        switchView.setClickable(false);
        row.addView(switchView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        card.addView(row);
        return card;
    }

    private MaterialCardView createBaseCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(getColor(R.color.app_surface));
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0xFFE8ECF2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private int grantedCount(List<PermissionItem> items) {
        int granted = 0;
        for (PermissionItem item : items) {
            if (item.isGranted(requireContext())) {
                granted++;
            }
        }
        return granted;
    }

    private List<PermissionItem> runtimePermissions() {
        List<PermissionItem> items = new ArrayList<>();
        items.add(PermissionItem.runtime(
                "Microphone Access",
                "Required to receive voice commands",
                "M",
                Manifest.permission.RECORD_AUDIO
        ));
        items.add(PermissionItem.runtime(
                "Contacts Access",
                "Required for contact calling commands",
                "C",
                Manifest.permission.READ_CONTACTS
        ));
        items.add(PermissionItem.runtime(
                "Phone Call Access",
                "Required to start phone calls",
                "P",
                Manifest.permission.CALL_PHONE
        ));
        items.add(PermissionItem.runtime(
                "Phone State Access",
                "Pause listening while calls are active",
                "S",
                Manifest.permission.READ_PHONE_STATE
        ));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            items.add(PermissionItem.runtime(
                    "Answer Call Access",
                    "Required for answer and reject call commands",
                    "A",
                    Manifest.permission.ANSWER_PHONE_CALLS
            ));
        }
        items.add(PermissionItem.runtime(
                "Camera Access",
                "Required for camera and flashlight commands",
                "K",
                Manifest.permission.CAMERA
        ));
        return items;
    }

    private List<PermissionItem> advancedPermissions() {
        List<PermissionItem> items = new ArrayList<>();
        items.add(PermissionItem.advanced(
                "Accessibility Service",
                "Control apps and perform actions",
                "A",
                isAccessibilityEnabled(),
                () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        ));
        items.add(PermissionItem.advanced(
                "Popup Access",
                "Display overlay windows on top of apps",
                "O",
                Settings.canDrawOverlays(requireContext()),
                () -> openSettings(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName())
                )
        ));
        items.add(PermissionItem.advanced(
                "Sound Mode Access",
                "Required for true silent mode control",
                "D",
                isNotificationPolicyAccessGranted(),
                () -> openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        ));
        items.add(PermissionItem.advanced(
                "System Settings Access",
                "Required for brightness control",
                "S",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(requireContext()),
                () -> openSettings(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName())
                )
        ));
        return items;
    }

    private void requestOrOpen(PermissionItem item) {
        if (item.runtimePermission != null) {
            if (!item.isGranted(requireContext())) {
                requestPermissions(new String[]{item.runtimePermission}, REQUEST_RUNTIME_PERMISSION);
            }
            return;
        }

        if (item.action != null) {
            item.action.run();
        }
    }

    private boolean isAccessibilityEnabled() {
        MainActivity activity = mainActivity();
        return activity != null && activity.isAssistantAccessibilityServiceEnabled();
    }

    private boolean isNotificationPolicyAccessGranted() {
        NotificationManager notificationManager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
    }

    private void openSettings(String action) {
        openSettings(action, null);
    }

    private void openSettings(String action, @Nullable Uri data) {
        Intent intent = new Intent(action);
        if (data != null) {
            intent.setData(data);
        }
        startActivity(intent);
    }

    private MainActivity mainActivity() {
        return getActivity() instanceof MainActivity
                ? (MainActivity) getActivity()
                : null;
    }

    private int getColor(int colorRes) {
        return requireContext().getColor(colorRes);
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private static final class PermissionGroup {
        final String title;
        final String description;
        final List<PermissionItem> items;

        private PermissionGroup(String title, String description, List<PermissionItem> items) {
            this.title = title;
            this.description = description;
            this.items = items;
        }

        static PermissionGroup appPermissions(List<PermissionItem> items) {
            return new PermissionGroup(
                    "App Permissions",
                    "Permissions confirmed directly in Android popups",
                    items
            );
        }

        static PermissionGroup advancedAccess(List<PermissionItem> items) {
            return new PermissionGroup(
                    "Advanced Access",
                    "Permissions activated from Android settings screens",
                    items
            );
        }
    }

    private static final class PermissionItem {
        final String title;
        final String description;
        final String iconText;
        final String runtimePermission;
        final Boolean granted;
        final Runnable action;

        private PermissionItem(
                String title,
                String description,
                String iconText,
                String runtimePermission,
                Boolean granted,
                Runnable action
        ) {
            this.title = title;
            this.description = description;
            this.iconText = iconText;
            this.runtimePermission = runtimePermission;
            this.granted = granted;
            this.action = action;
        }

        static PermissionItem runtime(
                String title,
                String description,
                String iconText,
                String permission
        ) {
            return new PermissionItem(title, description, iconText, permission, null, null);
        }

        static PermissionItem advanced(
                String title,
                String description,
                String iconText,
                boolean granted,
                Runnable action
        ) {
            return new PermissionItem(title, description, iconText, null, granted, action);
        }

        boolean isGranted(Context context) {
            if (runtimePermission != null) {
                return context.checkSelfPermission(runtimePermission) == PackageManager.PERMISSION_GRANTED;
            }
            return Boolean.TRUE.equals(granted);
        }
    }
}
