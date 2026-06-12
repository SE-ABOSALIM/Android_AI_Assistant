package com.example.anroidaiassistant.ui.screens;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.R;

import java.util.ArrayList;
import java.util.List;

public final class PermissionsFragment extends Fragment {
    private static final int REQUEST_RUNTIME_PERMISSION = 610;

    private LinearLayout summaryCard;
    private ImageView summaryIcon;
    private TextView summaryCount;
    private ProgressBar summaryProgress;
    private LinearLayout contentContainer;
    private PermissionGroup activeGroup;

    private final List<RowHolder> rowHolders = new ArrayList<>();

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
        summaryCard = view.findViewById(R.id.summary_card);
        summaryIcon = view.findViewById(R.id.summary_icon);
        summaryCount = view.findViewById(R.id.summary_count);
        summaryProgress = view.findViewById(R.id.summary_progress);
        contentContainer = view.findViewById(R.id.permissions_content_container);
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
        if (contentContainer == null || !isAdded()) {
            return;
        }

        PermissionGroup appPermissions = PermissionGroup.appPermissions(runtimePermissions());
        PermissionGroup advancedAccess = PermissionGroup.advancedAccess(advancedPermissions());

        rowHolders.clear();
        contentContainer.removeAllViews();

        if (activeGroup == null) {
            contentContainer.addView(createCategoryCard(appPermissions));
            contentContainer.addView(createCategoryCard(advancedAccess));
        } else {
            PermissionGroup group = activeGroup.kind == GroupKind.APP
                    ? appPermissions
                    : advancedAccess;
            activeGroup = group;
            contentContainer.addView(createDetailHeader(group));
            for (PermissionItem item : group.items) {
                rowHolders.add(addRow(contentContainer, item));
            }
        }

        refresh(appPermissions, advancedAccess);
    }

    private View createDetailHeader(PermissionGroup group) {
        Context context = requireContext();

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), dp(10));

        LinearLayout back = new LinearLayout(context);
        back.setOrientation(LinearLayout.HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(view -> {
            activeGroup = null;
            render();
        });

        ImageView backIcon = new ImageView(context);
        backIcon.setImageResource(R.drawable.ic_chevron_left);
        ImageViewCompat.setImageTintList(
                backIcon,
                ColorStateList.valueOf(context.getColor(R.color.app_primary))
        );
        back.addView(backIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = new TextView(context);
        title.setText("Permissions");
        title.setTextColor(context.getColor(R.color.app_primary));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        back.addView(title);

        header.addView(back, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView count = new TextView(context);
        int granted = grantedCount(group.items);
        boolean complete = granted == group.items.size();
        count.setText(granted + "/" + group.items.size() + " granted");
        count.setTextColor(statusColor(complete));
        count.setTextSize(12);
        count.setTypeface(count.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(count);

        return header;
    }

    private View createCategoryCard(PermissionGroup group) {
        Context context = requireContext();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            activeGroup = group;
            render();
        });

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(cardParams);

        int granted = grantedCount(group.items);
        boolean complete = granted == group.items.size();
        int accentColor = statusColor(complete);
        int softAccentColor = statusSoftColor(complete);

        ImageView icon = new ImageView(context);
        icon.setImageResource(group.iconRes);
        icon.setBackgroundResource(R.drawable.permission_chip_on_bg);
        ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(softAccentColor));
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(accentColor)
        );
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(group.title);
        title.setTextColor(context.getColor(R.color.app_text_primary));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        textColumn.addView(title);

        TextView description = new TextView(context);
        description.setText(group.description);
        description.setTextColor(context.getColor(R.color.app_text_secondary));
        description.setTextSize(12);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.setMargins(0, dp(3), 0, 0);
        textColumn.addView(description, descriptionParams);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMargins(dp(14), 0, dp(10), 0);
        card.addView(textColumn, textParams);

        TextView count = new TextView(context);
        count.setText(granted + "/" + group.items.size());
        count.setTextColor(accentColor);
        count.setTextSize(18);
        count.setTypeface(count.getTypeface(), android.graphics.Typeface.BOLD);
        count.setGravity(Gravity.CENTER);
        card.addView(count);

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        ImageViewCompat.setImageTintList(
                arrow,
                ColorStateList.valueOf(context.getColor(R.color.bottom_nav_inactive))
        );
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                dp(24),
                dp(24)
        );
        arrowParams.setMargins(dp(8), 0, 0, 0);
        card.addView(arrow, arrowParams);

        return card;
    }

    private RowHolder addRow(LinearLayout parent, PermissionItem item) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_permission_row, parent, false);

        ImageView icon = row.findViewById(R.id.permission_icon);
        TextView title = row.findViewById(R.id.permission_title);
        TextView description = row.findViewById(R.id.permission_description);
        SwitchCompat switchView = row.findViewById(R.id.permission_switch);

        icon.setImageResource(item.iconRes);
        title.setText(item.title);
        description.setText(item.description);

        row.setOnClickListener(v -> requestOrOpen(item));

        parent.addView(row);
        return new RowHolder(item, icon, switchView);
    }

    private void refresh(PermissionGroup appPermissions, PermissionGroup advancedAccess) {
        int total = appPermissions.items.size() + advancedAccess.items.size();
        int granted = grantedCount(appPermissions.items) + grantedCount(advancedAccess.items);
        boolean complete = granted == total;
        int accentColor = statusColor(complete);

        ViewCompat.setBackgroundTintList(summaryCard, ColorStateList.valueOf(statusSoftColor(complete)));
        summaryCount.setText(granted + "/" + total);
        summaryCount.setTextColor(accentColor);
        summaryProgress.setProgress(total == 0 ? 0 : Math.round(granted * 100f / total));
        summaryProgress.setProgressTintList(ColorStateList.valueOf(accentColor));
        summaryProgress.setProgressBackgroundTintList(ColorStateList.valueOf(
                complete
                        ? requireContext().getColor(R.color.app_progress_track)
                        : requireContext().getColor(R.color.app_warning_progress_track)
        ));

        summaryIcon.setImageResource(complete ? R.drawable.ic_perm_checklist : R.drawable.ic_perm_warning);
        summaryIcon.setBackgroundResource(R.drawable.summary_icon_bg);
        ViewCompat.setBackgroundTintList(summaryIcon, ColorStateList.valueOf(accentColor));
        ImageViewCompat.setImageTintList(summaryIcon, complete
                ? ColorStateList.valueOf(requireContext().getColor(R.color.white))
                : null);

        for (RowHolder holder : rowHolders) {
            holder.bind(holder.item.isGranted(requireContext(), this));
        }
    }

    private int grantedCount(List<PermissionItem> items) {
        int granted = 0;
        for (PermissionItem item : items) {
            if (item.isGranted(requireContext(), this)) {
                granted++;
            }
        }
        return granted;
    }

    private final class RowHolder {
        final PermissionItem item;
        final ImageView icon;
        final SwitchCompat switchView;

        RowHolder(PermissionItem item, ImageView icon, SwitchCompat switchView) {
            this.item = item;
            this.icon = icon;
            this.switchView = switchView;
        }

        void bind(boolean granted) {
            switchView.setChecked(granted);
            icon.setBackgroundResource(granted
                    ? R.drawable.permission_chip_on_bg
                    : R.drawable.permission_chip_off_bg);
            int tint = requireContext().getColor(granted
                    ? R.color.app_primary
                    : R.color.app_chip_off_icon);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint));
        }
    }

    private List<PermissionItem> runtimePermissions() {
        List<PermissionItem> items = new ArrayList<>();
        items.add(PermissionItem.runtime(
                "Microphone Access",
                "Required to receive voice commands",
                R.drawable.ic_perm_mic,
                Manifest.permission.RECORD_AUDIO
        ));
        items.add(PermissionItem.runtime(
                "Contacts Access",
                "Required for contact calling commands",
                R.drawable.ic_perm_contacts,
                Manifest.permission.READ_CONTACTS
        ));
        items.add(PermissionItem.runtime(
                "Phone Call Access",
                "Required to start phone calls",
                R.drawable.ic_perm_call,
                Manifest.permission.CALL_PHONE
        ));
        items.add(PermissionItem.runtime(
                "Phone State Access",
                "Pause listening while calls are active",
                R.drawable.ic_perm_phone_state,
                Manifest.permission.READ_PHONE_STATE
        ));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            items.add(PermissionItem.runtime(
                    "Answer Call Access",
                    "Required for answer and reject call commands",
                    R.drawable.ic_perm_answer,
                    Manifest.permission.ANSWER_PHONE_CALLS
            ));
        }
        items.add(PermissionItem.runtime(
                "Camera Access",
                "Required for camera and flashlight commands",
                R.drawable.ic_perm_camera,
                Manifest.permission.CAMERA
        ));
        return items;
    }

    private List<PermissionItem> advancedPermissions() {
        List<PermissionItem> items = new ArrayList<>();
        items.add(PermissionItem.advanced(
                "Accessibility Service",
                "Control apps and perform actions",
                R.drawable.ic_perm_accessibility,
                fragment -> fragment.isAccessibilityEnabled(),
                this::openAccessibilityServiceSettings
        ));
        items.add(PermissionItem.advanced(
                "Popup Access",
                "Display overlay windows on top of apps",
                R.drawable.ic_perm_popup,
                fragment -> Settings.canDrawOverlays(fragment.requireContext()),
                () -> openSettings(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName())
                )
        ));
        items.add(PermissionItem.advanced(
                "Sound Mode Access",
                "Required for true silent mode control",
                R.drawable.ic_perm_sound,
                fragment -> fragment.isNotificationPolicyAccessGranted(),
                () -> openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        ));
        items.add(PermissionItem.advanced(
                "System Settings Access",
                "Required for brightness control",
                R.drawable.ic_perm_brightness,
                fragment -> Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || Settings.System.canWrite(fragment.requireContext()),
                () -> openSettings(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName())
                )
        ));
        return items;
    }

    private void requestOrOpen(PermissionItem item) {
        if (item.runtimePermission != null) {
            if (!item.isGranted(requireContext(), this)) {
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

    private void openAccessibilityServiceSettings() {
        ComponentName serviceComponent = new ComponentName(
                requireContext(),
                MyAccessibilityService.class
        );
        Intent detailsIntent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        detailsIntent.putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent);

        try {
            startActivity(detailsIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
    }

    private MainActivity mainActivity() {
        return getActivity() instanceof MainActivity
                ? (MainActivity) getActivity()
                : null;
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private int statusColor(boolean complete) {
        return requireContext().getColor(complete ? R.color.app_primary : R.color.app_warning);
    }

    private int statusSoftColor(boolean complete) {
        return requireContext().getColor(complete ? R.color.app_primary_soft : R.color.app_warning_soft);
    }

    private enum GroupKind {
        APP,
        ADVANCED
    }

    private static final class PermissionGroup {
        final GroupKind kind;
        final String title;
        final String description;
        @DrawableRes final int iconRes;
        final List<PermissionItem> items;

        private PermissionGroup(
                GroupKind kind,
                String title,
                String description,
                @DrawableRes int iconRes,
                List<PermissionItem> items
        ) {
            this.kind = kind;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.items = items;
        }

        static PermissionGroup appPermissions(List<PermissionItem> items) {
            return new PermissionGroup(
                    GroupKind.APP,
                    "App Permissions",
                    "Permissions confirmed directly in Android popups",
                    R.drawable.ic_perm_shield,
                    items
            );
        }

        static PermissionGroup advancedAccess(List<PermissionItem> items) {
            return new PermissionGroup(
                    GroupKind.ADVANCED,
                    "Advanced Access",
                    "Permissions activated from Android settings screens",
                    R.drawable.ic_perm_accessibility,
                    items
            );
        }
    }

    private interface GrantedCheck {
        boolean isGranted(PermissionsFragment fragment);
    }

    private static final class PermissionItem {
        final String title;
        final String description;
        @DrawableRes final int iconRes;
        @Nullable final String runtimePermission;
        @Nullable final GrantedCheck advancedCheck;
        @Nullable final Runnable action;

        private PermissionItem(
                String title,
                String description,
                @DrawableRes int iconRes,
                @Nullable String runtimePermission,
                @Nullable GrantedCheck advancedCheck,
                @Nullable Runnable action
        ) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.runtimePermission = runtimePermission;
            this.advancedCheck = advancedCheck;
            this.action = action;
        }

        static PermissionItem runtime(
                String title,
                String description,
                @DrawableRes int iconRes,
                String permission
        ) {
            return new PermissionItem(title, description, iconRes, permission, null, null);
        }

        static PermissionItem advanced(
                String title,
                String description,
                @DrawableRes int iconRes,
                GrantedCheck check,
                Runnable action
        ) {
            return new PermissionItem(title, description, iconRes, null, check, action);
        }

        boolean isGranted(Context context, PermissionsFragment fragment) {
            if (runtimePermission != null) {
                return context.checkSelfPermission(runtimePermission)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return advancedCheck != null && advancedCheck.isGranted(fragment);
        }
    }
}
