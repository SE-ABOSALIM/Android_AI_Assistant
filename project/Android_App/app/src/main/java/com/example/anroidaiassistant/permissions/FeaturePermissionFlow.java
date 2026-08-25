package com.example.anroidaiassistant.permissions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

public final class FeaturePermissionFlow {
    public static final String EXTRA_REQUEST_CAPABILITY =
            "com.example.anroidaiassistant.extra.REQUEST_FEATURE_CAPABILITY";
    public static final int REQUEST_CODE_OPTIONAL_RUNTIME_PERMISSION = 720;

    private static final String PREFERENCES_NAME = "feature_permission_requests";
    private static final String REQUESTED_PREFIX = "requested_";
    private static final Object LAUNCH_LOCK = new Object();
    private static final FeaturePermissionCoordinator COORDINATOR =
            new FeaturePermissionCoordinator(System::currentTimeMillis);

    private static PendingLaunch pendingLaunch;
    private static WeakReference<AlertDialog> activeDialogReference =
            new WeakReference<>(null);

    private FeaturePermissionFlow() {}

    public static void requestFromFeature(
            Context context,
            AssistantCapability capability,
            Runnable onGranted,
            Runnable onCancelled
    ) {
        if (context == null || capability == null || capability.isCore()) {
            run(onCancelled);
            return;
        }

        PendingLaunch replaced;
        synchronized (LAUNCH_LOCK) {
            replaced = pendingLaunch;
            pendingLaunch = new PendingLaunch(capability, onGranted, onCancelled);
        }
        if (replaced != null) {
            run(replaced.onCancelled);
        }

        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_REQUEST_CAPABILITY, capability.name())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            PendingLaunch failed = takePendingLaunch(capability);
            if (failed != null) {
                run(failed.onCancelled);
            }
        }
    }

    public static void requestFromPermissionsScreen(
            Activity activity,
            AssistantCapability capability,
            Runnable onFinished
    ) {
        requestFromFeature(activity, capability, onFinished, onFinished);
    }

    public static void handleIntent(Activity activity, Intent intent) {
        if (activity == null || intent == null) {
            return;
        }

        String capabilityName = intent.getStringExtra(EXTRA_REQUEST_CAPABILITY);
        if (capabilityName == null) {
            return;
        }
        intent.removeExtra(EXTRA_REQUEST_CAPABILITY);

        AssistantCapability capability;
        try {
            capability = AssistantCapability.valueOf(capabilityName);
        } catch (IllegalArgumentException exception) {
            return;
        }

        PendingLaunch launch = takePendingLaunch(capability);
        if (launch == null) {
            return;
        }

        AndroidHost host = new AndroidHost(activity);
        boolean alreadyGranted = COORDINATOR.ensureGranted(
                host,
                capability,
                launch.onGranted,
                launch.onCancelled
        );
        if (alreadyGranted) {
            run(launch.onGranted);
        }
    }

    public static void onActivityResumed(Activity activity) {
        if (activity != null) {
            COORDINATOR.onSettingsReturned(new AndroidHost(activity));
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        if (activity == null || !activity.isChangingConfigurations()) {
            return;
        }
        AlertDialog dialog = activeDialogReference.get();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        COORDINATOR.cancelPendingAction();
    }

    public static boolean onRequestPermissionsResult(
            Activity activity,
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        if (requestCode != REQUEST_CODE_OPTIONAL_RUNTIME_PERMISSION || activity == null) {
            return false;
        }

        AssistantCapability capability = COORDINATOR.pendingCapability();
        if (capability == null) {
            return true;
        }
        String expectedPermission = FeaturePermissionAccess.runtimePermission(capability);
        boolean granted = false;
        if (permissions != null && grantResults != null && expectedPermission != null) {
            int resultCount = Math.min(permissions.length, grantResults.length);
            for (int index = 0; index < resultCount; index++) {
                if (expectedPermission.equals(permissions[index])) {
                    granted = grantResults[index] == PackageManager.PERMISSION_GRANTED;
                    break;
                }
            }
        }
        COORDINATOR.onRuntimePermissionResult(
                new AndroidHost(activity),
                capability,
                granted
        );
        return true;
    }

    private static PendingLaunch takePendingLaunch(AssistantCapability capability) {
        synchronized (LAUNCH_LOCK) {
            if (pendingLaunch == null || pendingLaunch.capability != capability) {
                return null;
            }
            PendingLaunch result = pendingLaunch;
            pendingLaunch = null;
            return result;
        }
    }

    private static int titleRes(AssistantCapability capability) {
        switch (capability) {
            case CONTACTS:
                return R.string.permission_contacts_title;
            case DIRECT_CALL:
                return R.string.permission_call_title;
            case PHONE_STATE:
                return R.string.permission_phone_state_title;
            case ANSWER_CALL:
                return R.string.permission_answer_call_title;
            case CAMERA:
                return R.string.permission_camera_title;
            case SOUND_MODE:
                return R.string.permission_sound_title;
            case SYSTEM_SETTINGS:
                return R.string.permission_system_settings_title;
            default:
                return R.string.feature_permission_title;
        }
    }

    private static int explanationRes(AssistantCapability capability) {
        switch (capability) {
            case CONTACTS:
                return R.string.feature_permission_contacts_explanation;
            case DIRECT_CALL:
                return R.string.feature_permission_call_explanation;
            case PHONE_STATE:
                return R.string.feature_permission_phone_state_explanation;
            case ANSWER_CALL:
                return R.string.feature_permission_answer_call_explanation;
            case CAMERA:
                return R.string.feature_permission_camera_explanation;
            case SOUND_MODE:
                return R.string.feature_permission_sound_explanation;
            case SYSTEM_SETTINGS:
                return R.string.feature_permission_system_settings_explanation;
            default:
                return R.string.feature_permission_generic_explanation;
        }
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private static final class PendingLaunch {
        final AssistantCapability capability;
        final Runnable onGranted;
        final Runnable onCancelled;

        PendingLaunch(
                AssistantCapability capability,
                Runnable onGranted,
                Runnable onCancelled
        ) {
            this.capability = capability;
            this.onGranted = onGranted;
            this.onCancelled = onCancelled;
        }
    }

    private static final class AndroidHost implements FeaturePermissionCoordinator.Host {
        private final WeakReference<Activity> activityReference;

        AndroidHost(Activity activity) {
            activityReference = new WeakReference<>(activity);
        }

        @Override
        public boolean isGranted(AssistantCapability capability) {
            Activity activity = activityReference.get();
            return activity != null && FeaturePermissionAccess.isGranted(activity, capability);
        }

        @Override
        public boolean isPermanentlyDenied(AssistantCapability capability) {
            Activity activity = activityReference.get();
            String permission = FeaturePermissionAccess.runtimePermission(capability);
            if (activity == null
                    || permission == null
                    || activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            SharedPreferences preferences = activity.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
            );
            return preferences.getBoolean(REQUESTED_PREFIX + permission, false)
                    && !activity.shouldShowRequestPermissionRationale(permission);
        }

        @Override
        public void showExplanation(
                AssistantCapability capability,
                boolean permanentlyDenied,
                Runnable onAccepted,
                Runnable onCancelled
        ) {
            Activity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                run(onCancelled);
                return;
            }

            AlertDialog activeDialog = activeDialogReference.get();
            if (activeDialog != null && activeDialog.isShowing()) {
                activeDialog.dismiss();
            }

            int messageRes = permanentlyDenied
                    ? R.string.feature_permission_permanently_denied_explanation
                    : explanationRes(capability);
            int positiveRes = permanentlyDenied
                    ? R.string.feature_permission_open_settings
                    : R.string.feature_permission_activate;
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle(titleRes(capability))
                    .setMessage(messageRes)
                    .setPositiveButton(positiveRes, (ignored, which) -> run(onAccepted))
                    .setNegativeButton(
                            R.string.feature_permission_not_now,
                            (ignored, which) -> run(onCancelled)
                    )
                    .create();
            activeDialogReference = new WeakReference<>(dialog);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnCancelListener(ignored -> run(onCancelled));
            dialog.setOnDismissListener(ignored -> {
                if (activeDialogReference.get() == dialog) {
                    activeDialogReference.clear();
                }
            });
            dialog.show();
        }

        @Override
        public void requestRuntimePermission(AssistantCapability capability) {
            Activity activity = activityReference.get();
            String permission = FeaturePermissionAccess.runtimePermission(capability);
            if (activity == null || permission == null) {
                COORDINATOR.cancelPendingAction();
                return;
            }
            activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(REQUESTED_PREFIX + permission, true)
                    .apply();
            activity.requestPermissions(
                    new String[]{permission},
                    REQUEST_CODE_OPTIONAL_RUNTIME_PERMISSION
            );
        }

        @Override
        public void openSpecialAccessSettings(AssistantCapability capability) {
            Activity activity = activityReference.get();
            if (activity == null) {
                COORDINATOR.cancelPendingAction();
                return;
            }

            Intent intent;
            if (capability == AssistantCapability.SOUND_MODE) {
                intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            } else if (capability == AssistantCapability.SYSTEM_SETTINGS) {
                intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .setData(Uri.parse("package:" + activity.getPackageName()));
            } else {
                COORDINATOR.cancelPendingAction();
                return;
            }
            try {
                activity.startActivity(intent);
            } catch (RuntimeException exception) {
                COORDINATOR.cancelPendingAction();
                Toast.makeText(
                        activity,
                        R.string.feature_permission_settings_unavailable,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        @Override
        public void openApplicationSettings(AssistantCapability capability) {
            Activity activity = activityReference.get();
            if (activity == null) {
                COORDINATOR.cancelPendingAction();
                return;
            }
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (RuntimeException exception) {
                COORDINATOR.cancelPendingAction();
                Toast.makeText(
                        activity,
                        R.string.feature_permission_settings_unavailable,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        @Override
        public void showDeniedFeedback(AssistantCapability capability) {
            Activity activity = activityReference.get();
            if (activity != null && !activity.isFinishing()) {
                Toast.makeText(
                        activity,
                        R.string.feature_permission_denied,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }
}
