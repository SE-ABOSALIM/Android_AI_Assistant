package com.example.anroidaiassistant.accessibility;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.anroidaiassistant.MyAccessibilityService;
import com.example.anroidaiassistant.resources.SearchInputAliases;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class SearchInputController {
    private static final int SEARCH_FIELD_RETRY_DELAY_MS = 450;
    private static final int SEARCH_FIELD_MAX_RETRIES = 5;
    private static final int SUBMIT_AFTER_SET_TEXT_DELAY_MS = 500;
    private static final int MIN_DIRECT_SEARCH_INPUT_SCORE = 8;
    private static final int MIN_SEARCH_BUTTON_SCORE = 8;

    private final MyAccessibilityService service;
    private final Handler mainHandler;
    private final GestureController gestureController;

    public SearchInputController(
            MyAccessibilityService service,
            Handler mainHandler,
            GestureController gestureController
    ) {
        this.service = service;
        this.mainHandler = mainHandler;
        this.gestureController = gestureController;
    }

    public boolean performSearch(String query) {
        if (!TextNormalizer.hasText(query)) {
            return false;
        }

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        AccessibilityNodeInfo searchInput = findBestSearchInput(rootNode, MIN_DIRECT_SEARCH_INPUT_SCORE);
        if (searchInput != null) {
            return writeQueryAndSubmit(searchInput, query);
        }

        AccessibilityNodeInfo searchButton = findSearchButton(rootNode);
        if (searchButton == null || !clickNode(searchButton)) {
            return false;
        }

        retrySearchAfterButtonClick(query, 1);
        return true;
    }

    public boolean hasSearchInputAvailable() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        return findBestSearchInput(rootNode, MIN_DIRECT_SEARCH_INPUT_SCORE) != null || findSearchButton(rootNode) != null;
    }

    public boolean writeText(String text) {
        if (!TextNormalizer.hasText(text)) {
            return false;
        }

        AccessibilityNodeInfo inputNode = findBestWritableInput();
        return inputNode != null && setText(inputNode, text);
    }

    public boolean clearText() {
        AccessibilityNodeInfo inputNode = findBestWritableInput();
        return inputNode != null && setText(inputNode, "");
    }

    public boolean pressKeyboardAction(String actionText) {
        if (!isKeyboardActionText(actionText)) {
            return false;
        }

        AccessibilityNodeInfo inputNode = findBestWritableInput();
        return inputNode != null && submitSearch(inputNode);
    }

    private boolean writeQueryAndSubmit(AccessibilityNodeInfo inputNode, String query) {
        boolean textSet = setText(inputNode, query);
        if (!textSet) {
            return false;
        }

        mainHandler.postDelayed(() -> {
            AccessibilityNodeInfo refreshedInput = findBestWritableInput();
            submitSearch(refreshedInput != null ? refreshedInput : inputNode);
        }, SUBMIT_AFTER_SET_TEXT_DELAY_MS);
        return true;
    }

    private void retrySearchAfterButtonClick(String query, int attempt) {
        mainHandler.postDelayed(() -> {
            AccessibilityNodeInfo refreshedRoot = service.getRootInActiveWindow();
            AccessibilityNodeInfo refreshedInput = findBestSearchInput(refreshedRoot, MIN_DIRECT_SEARCH_INPUT_SCORE);
            if (refreshedInput == null) {
                refreshedInput = findBestWritableInput();
            }

            if (refreshedInput != null) {
                writeQueryAndSubmit(refreshedInput, query);
                return;
            }

            if (attempt < SEARCH_FIELD_MAX_RETRIES) {
                retrySearchAfterButtonClick(query, attempt + 1);
            }
        }, SEARCH_FIELD_RETRY_DELAY_MS);
    }

    private boolean setText(AccessibilityNodeInfo inputNode, String text) {
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
        );

        return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private boolean submitSearch(AccessibilityNodeInfo inputNode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return inputNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
        return false;
    }

    private AccessibilityNodeInfo findBestSearchInput(AccessibilityNodeInfo node) {
        return findBestSearchInput(node, 0);
    }

    private AccessibilityNodeInfo findBestSearchInput(AccessibilityNodeInfo node, int minScore) {
        SearchResult result = findBestSearchInputResult(node, null, -1);
        return result.score >= minScore ? result.node : null;
    }

    private AccessibilityNodeInfo findBestSearchInput(
            AccessibilityNodeInfo node,
            AccessibilityNodeInfo bestNode,
            int bestScore
    ) {
        if (node == null) {
            return bestNode;
        }

        int score = scoreSearchInput(node);
        if (score > bestScore) {
            bestNode = node;
            bestScore = score;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            SearchResult result = findBestSearchInputResult(node.getChild(i), bestNode, bestScore);
            bestNode = result.node;
            bestScore = result.score;
        }

        return bestNode;
    }

    private SearchResult findBestSearchInputResult(
            AccessibilityNodeInfo node,
            AccessibilityNodeInfo bestNode,
            int bestScore
    ) {
        AccessibilityNodeInfo resultNode = findBestSearchInput(node, bestNode, bestScore);
        int resultScore = resultNode == bestNode ? bestScore : scoreSearchInput(resultNode);
        return new SearchResult(resultNode, resultScore);
    }

    private AccessibilityNodeInfo findBestWritableInput() {
        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) {
            return null;
        }

        List<AccessibilityNodeInfo> inputs = new ArrayList<>();
        collectTextInputs(rootNode, inputs);
        for (AccessibilityNodeInfo input : inputs) {
            if (input.isFocused()) {
                return input;
            }
        }

        if (inputs.size() == 1) {
            return inputs.get(0);
        }
        return null;
    }

    private void collectTextInputs(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> inputs) {
        if (node == null) {
            return;
        }

        if (isTextInput(node) && (node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0) {
            inputs.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectTextInputs(node.getChild(i), inputs);
        }
    }

    private int scoreSearchInput(AccessibilityNodeInfo node) {
        if (!isTextInput(node)) {
            return -1;
        }

        int score = 1;
        String nodeText = normalize(joinNodeText(node));
        if (containsSearchKeyword(nodeText)) {
            score += 10;
        }
        if (node.isFocused()) {
            score += 2;
        }
        if ((node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) != 0) {
            score += 3;
        }
        return score;
    }

    private AccessibilityNodeInfo findSearchButton(AccessibilityNodeInfo node) {
        SearchButtonResult result = findBestSearchButton(node, null, -1);
        return result.score >= MIN_SEARCH_BUTTON_SCORE ? result.node : null;
    }

    private SearchButtonResult findBestSearchButton(
            AccessibilityNodeInfo node,
            AccessibilityNodeInfo bestNode,
            int bestScore
    ) {
        if (node == null) {
            return new SearchButtonResult(bestNode, bestScore);
        }

        int score = scoreSearchButton(node);
        if (score > bestScore) {
            AccessibilityNodeInfo clickableNode = findClickableNode(node);
            if (clickableNode != null) {
                bestNode = clickableNode;
                bestScore = score;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            SearchButtonResult childResult = findBestSearchButton(node.getChild(i), bestNode, bestScore);
            bestNode = childResult.node;
            bestScore = childResult.score;
        }

        return new SearchButtonResult(bestNode, bestScore);
    }

    private int scoreSearchButton(AccessibilityNodeInfo node) {
        if (isTextInput(node)) {
            return -1;
        }

        String nodeText = normalize(joinNodeText(node));
        if (!containsSearchKeyword(nodeText) || isSecondarySearchAction(nodeText)) {
            return -1;
        }

        AccessibilityNodeInfo clickableNode = findClickableNode(node);
        if (clickableNode == null) {
            return -1;
        }

        Rect bounds = new Rect();
        clickableNode.getBoundsInScreen(bounds);
        int score = 6;
        if (equalsAny(nodeText, SearchInputAliases.EXACT_SEARCH_BUTTON_LABELS)) {
            score += 8;
        }
        if (bounds.width() >= service.getResources().getDisplayMetrics().widthPixels * 0.45f) {
            score += 14;
        }
        if (bounds.height() >= 40) {
            score += 2;
        }
        return score;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo clickableNode = findClickableNode(node);
        if (clickableNode == null) {
            return false;
        }
        if (clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return gestureController != null && gestureController.tapNodeCenter(clickableNode);
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()
                    || (current.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isTextInput(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        String normalizedClassName = className == null ? "" : className.toString().toLowerCase();
        return node.isEditable() || normalizedClassName.contains("edittext");
    }

    private boolean containsSearchKeyword(String value) {
        return containsAny(value, SearchInputAliases.SEARCH_KEYWORDS);
    }

    private boolean isSecondarySearchAction(String value) {
        return containsAny(value, SearchInputAliases.SECONDARY_SEARCH_ACTION_KEYWORDS);
    }

    private boolean isKeyboardActionText(String value) {
        String normalized = normalizeKeyboardActionText(value);
        return equalsAny(normalized, SearchInputAliases.KEYBOARD_ACTIONS);
    }

    private String normalizeKeyboardActionText(String value) {
        String normalized = normalize(value);
        for (String filler : SearchInputAliases.KEYBOARD_ACTION_FILLERS) {
            normalized = normalized.replace(filler, " ");
        }
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAny(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String joinNodeText(AccessibilityNodeInfo node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node.getText());
        append(builder, node.getContentDescription());
        append(builder, node.getViewIdResourceName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append(builder, node.getHintText());
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private String normalize(String value) {
        return TextNormalizer.normalizeAsciiText(value).toLowerCase();
    }

    private static final class SearchResult {
        private final AccessibilityNodeInfo node;
        private final int score;

        private SearchResult(AccessibilityNodeInfo node, int score) {
            this.node = node;
            this.score = score;
        }
    }

    private static final class SearchButtonResult {
        private final AccessibilityNodeInfo node;
        private final int score;

        private SearchButtonResult(AccessibilityNodeInfo node, int score) {
            this.node = node;
            this.score = score;
        }
    }
}
