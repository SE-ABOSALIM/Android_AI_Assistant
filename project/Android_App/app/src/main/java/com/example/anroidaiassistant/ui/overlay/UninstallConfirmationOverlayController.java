package com.example.anroidaiassistant.ui.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.settings.AssistantSettings;
import com.example.anroidaiassistant.util.TextNormalizer;

public final class UninstallConfirmationOverlayController {
    public interface Listener {
        void onConfirmRequested();
        void onRejectRequested();
        void onConfirmationDismissed();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;

    private View overlayView;
    private ImageView appIconView;
    private TextView appNameView;
    private TextView questionView;
    private TextView yesView;
    private TextView noView;
    private TextView hintView;

    public UninstallConfirmationOverlayController(
            Context context,
            WindowManager windowManager,
            Listener listener
    ) {
        this.context = context;
        this.windowManager = windowManager;
        this.listener = listener;
    }

    public void show(
            String appName,
            Drawable appIcon,
            String question,
            String yesText,
            String noText,
            String hint
    ) {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(context)
                    .inflate(R.layout.uninstall_confirmation_overlay_layout, null);
            appIconView = overlayView.findViewById(R.id.iv_uninstall_app_icon);
            appNameView = overlayView.findViewById(R.id.tv_uninstall_app_name);
            questionView = overlayView.findViewById(R.id.tv_uninstall_question);
            yesView = overlayView.findViewById(R.id.tv_uninstall_yes);
            noView = overlayView.findViewById(R.id.tv_uninstall_no);
            hintView = overlayView.findViewById(R.id.tv_uninstall_hint);
            View window = overlayView.findViewById(R.id.uninstall_confirmation_window);

            overlayView.setFocusable(true);
            overlayView.setFocusableInTouchMode(true);
            overlayView.setOnClickListener(view -> listener.onConfirmationDismissed());
            overlayView.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        listener.onConfirmationDismissed();
                    }
                    return true;
                }
                return false;
            });

            if (window != null) {
                window.setOnClickListener(view -> {});
            }

            yesView.setOnClickListener(view -> listener.onConfirmRequested());
            noView.setOnClickListener(view -> listener.onRejectRequested());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;
            windowManager.addView(overlayView, params);
            overlayView.requestFocus();
        }

        update(appName, appIcon, question, yesText, noText, hint);
    }

    public void hide() {
        if (overlayView == null) {
            return;
        }

        windowManager.removeView(overlayView);
        overlayView = null;
        appIconView = null;
        appNameView = null;
        questionView = null;
        yesView = null;
        noView = null;
        hintView = null;
    }

    private void update(
            String appName,
            Drawable appIcon,
            String question,
            String yesText,
            String noText,
            String hint
    ) {
        boolean rtl = AssistantSettings.isRtl(context);
        if (overlayView != null) {
            overlayView.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
        appNameView.setText(TextNormalizer.hasText(appName)
                ? appName
                : context.getString(R.string.uninstall_app_fallback));
        if (appIcon != null) {
            appIconView.setImageDrawable(appIcon);
        } else {
            appIconView.setImageResource(R.mipmap.ic_launcher);
        }
        questionView.setText(TextNormalizer.hasText(question)
                ? question
                : context.getString(R.string.uninstall_question_fallback));
        yesView.setText(TextNormalizer.hasText(yesText)
                ? yesText
                : context.getString(R.string.uninstall_yes_fallback));
        noView.setText(TextNormalizer.hasText(noText)
                ? noText
                : context.getString(R.string.uninstall_no_fallback));
        hintView.setText(TextNormalizer.hasText(hint)
                ? hint
                : context.getString(R.string.uninstall_hint_fallback));
    }
}
