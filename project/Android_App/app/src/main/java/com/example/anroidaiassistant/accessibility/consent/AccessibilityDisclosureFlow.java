package com.example.anroidaiassistant.accessibility.consent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.example.anroidaiassistant.MainActivity;
import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

public final class AccessibilityDisclosureFlow {
    public static final String EXTRA_REQUEST_DISCLOSURE =
            "com.example.anroidaiassistant.extra.REQUEST_ACCESSIBILITY_DISCLOSURE";
    private static WeakReference<AlertDialog> activeDialogReference = new WeakReference<>(null);

    private AccessibilityDisclosureFlow() {}

    public static void show(Activity activity, boolean serviceEnabled) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        AccessibilityDisclosureConsent consent = new AccessibilityDisclosureConsent(activity);
        AccessibilitySetupCoordinator coordinator = new AccessibilitySetupCoordinator(consent);
        coordinator.requestSetup(serviceEnabled, new AccessibilitySetupCoordinator.Host() {
            @Override
            public void showDisclosure(AccessibilitySetupCoordinator.Decision decision) {
                showDisclosureDialog(activity, decision);
            }

            @Override
            public void openAccessibilitySettings() {
                AccessibilityDisclosureFlow.openAccessibilitySettings(activity);
            }
        });
    }

    public static void requestFromAutomation(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_REQUEST_DISCLOSURE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private static void showDisclosureDialog(
            Activity activity,
            AccessibilitySetupCoordinator.Decision decision
    ) {
        AlertDialog activeDialog = activeDialogReference.get();
        if (activeDialog != null && activeDialog.isShowing()) {
            return;
        }

        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_accessibility_disclosure, null, false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.accessibility_disclosure_title)
                .setView(content)
                .setPositiveButton(
                        R.string.accessibility_disclosure_agree,
                        (ignored, which) -> decision.accept()
                )
                .setNegativeButton(
                        R.string.accessibility_disclosure_not_now,
                        (ignored, which) -> decision.decline()
                )
                .create();
        activeDialogReference = new WeakReference<>(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(ignored -> decision.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (activeDialogReference.get() == dialog) {
                activeDialogReference.clear();
            }
        });
        dialog.show();
    }

    private static void openAccessibilitySettings(Activity activity) {
        ComponentName serviceComponent = new ComponentName(activity, MyAccessibilityService.class);
        Intent detailsIntent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent);
        try {
            activity.startActivity(detailsIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }
}
