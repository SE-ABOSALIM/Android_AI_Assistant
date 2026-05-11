package com.example.anroidaiassistant.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.Collections;
import java.util.List;

public final class SelectionOverlayController {
    public interface Listener {
        void onChoiceSelected(int selectedIndex);
        void onSelectionCancelled();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;

    private View selectionOverlayView;
    private TextView selectionTitleView;
    private LinearLayout selectionChoiceContainer;
    private TextView selectionHintView;

    public SelectionOverlayController(Context context, WindowManager windowManager, Listener listener) {
        this.context = context;
        this.windowManager = windowManager;
        this.listener = listener;
    }

    public void show(String title, List<MyAccessibilityService.NumberedChoice> choices) {
        if (selectionOverlayView == null) {
            selectionOverlayView = LayoutInflater.from(context).inflate(R.layout.selection_overlay_layout, null);
            selectionTitleView = selectionOverlayView.findViewById(R.id.tv_selection_title);
            selectionChoiceContainer = selectionOverlayView.findViewById(R.id.selection_choice_container);
            selectionHintView = selectionOverlayView.findViewById(R.id.tv_selection_hint);
            View selectionWindow = selectionOverlayView.findViewById(R.id.selection_window);

            selectionOverlayView.setFocusable(true);
            selectionOverlayView.setFocusableInTouchMode(true);
            selectionOverlayView.setOnClickListener(view -> listener.onSelectionCancelled());
            selectionOverlayView.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        listener.onSelectionCancelled();
                    }
                    return true;
                }
                return false;
            });

            if (selectionWindow != null) {
                selectionWindow.setOnClickListener(view -> {});
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.CENTER;
            windowManager.addView(selectionOverlayView, params);
            selectionOverlayView.requestFocus();
        }

        update(title, choices == null ? Collections.emptyList() : choices);
    }

    public void hide() {
        if (selectionOverlayView == null) {
            return;
        }

        windowManager.removeView(selectionOverlayView);
        selectionOverlayView = null;
        selectionTitleView = null;
        selectionChoiceContainer = null;
        selectionHintView = null;
    }

    private void update(String title, List<MyAccessibilityService.NumberedChoice> choices) {
        if (selectionOverlayView == null || selectionChoiceContainer == null) {
            return;
        }

        selectionTitleView.setText(TextNormalizer.hasText(title) ? title : "Choose");
        selectionChoiceContainer.removeAllViews();

        for (int i = 0; i < choices.size(); i++) {
            selectionChoiceContainer.addView(createSelectionRow(i, choices.get(i)));
        }

        selectionHintView.setText("Birden cok secenek bulundu. Hangisini isterseniz numarasini soyleyin.");
        selectionHintView.setTextColor(Color.parseColor("#B9C0CC"));
    }

    private View createSelectionRow(int index, MyAccessibilityService.NumberedChoice choice) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setClickable(true);
        row.setOnClickListener(view -> listener.onChoiceSelected(index));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(6), 0, 0);
        row.setLayoutParams(rowParams);

        TextView number = new TextView(context);
        number.setText(String.valueOf(index + 1));
        number.setTextColor(Color.WHITE);
        number.setTextSize(15);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(number.getTypeface(), android.graphics.Typeface.BOLD);
        number.setBackgroundResource(R.drawable.selection_number_background);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        numberParams.setMargins(0, 0, dp(12), 0);
        row.addView(number, numberParams);

        if (choice.icon != null) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(choice.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            iconParams.setMargins(0, 0, dp(12), 0);
            row.addView(icon, iconParams);
        }

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView rowTitle = new TextView(context);
        rowTitle.setText(choice.title);
        rowTitle.setTextColor(Color.WHITE);
        rowTitle.setTextSize(16);
        rowTitle.setTypeface(rowTitle.getTypeface(), android.graphics.Typeface.BOLD);
        rowTitle.setSingleLine(false);
        textColumn.addView(rowTitle);

        if (TextNormalizer.hasText(choice.subtitle)) {
            TextView subtitle = new TextView(context);
            subtitle.setText(choice.subtitle);
            subtitle.setTextColor(Color.parseColor("#B9C0CC"));
            subtitle.setTextSize(13);
            subtitle.setSingleLine(false);
            textColumn.addView(subtitle);
        }

        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        return row;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}