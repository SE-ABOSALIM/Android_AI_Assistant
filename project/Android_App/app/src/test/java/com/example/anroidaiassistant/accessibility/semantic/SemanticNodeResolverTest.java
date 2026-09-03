package com.example.anroidaiassistant.accessibility.semantic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SemanticNodeResolverTest {
    private final FakeAdapter adapter = new FakeAdapter();
    private final SemanticNodeResolver<FakeNode> resolver = new SemanticNodeResolver<>(adapter);

    @Test
    public void resolvesHintOnlyEditableInputAsItsOwnActionTarget() {
        FakeNode input = node("name-input").editable("Name");

        SemanticNodeResolver.Resolution<FakeNode> resolution = resolver.resolveActionNode(input);

        assertSame(input, resolution.getActionNode());
        assertTrue(resolver.semanticValues(input, input).contains("Name"));
    }

    @Test
    public void resolvesExplicitLabelForRelationshipToInput() {
        FakeNode input = node("name-input").editable(null);
        FakeNode label = node("name-label").text("Name").labelFor(input);
        FakeNode root = node("root").child(label).child(input);

        SemanticNodeResolver.Resolution<FakeNode> resolution = resolver.resolveActionNode(label);

        assertSame(input, resolution.getActionNode());
        assertTrue(resolution.usesActionBounds());
        assertTrue(resolver.semanticValues(root, input).contains("Name"));
    }

    @Test
    public void resolvesTextChildToClickableParent() {
        FakeNode action = node("create-account-action").clickable();
        FakeNode text = node("create-account-text").text("Create a new account");
        action.child(text);

        assertSame(action, resolver.resolveActionNode(text).getActionNode());
    }

    @Test
    public void resolvesActionableForgotPasswordNode() {
        FakeNode link = node("forgot-password").text("Forgot password").clickable();

        assertSame(link, resolver.resolveActionNode(link).getActionNode());
    }

    @Test
    public void rejectsInertTextWithoutSemanticActionRelationship() {
        FakeNode text = node("inert").text("Name");

        assertNull(resolver.resolveActionNode(text));
    }

    @Test
    public void resolvesFocusableSpinnerAndCheckableControl() {
        FakeNode spinner = node("country").role("android.widget.Spinner").focusable();
        FakeNode checkbox = node("remember-me").text("Remember me").checkable();

        assertSame(spinner, resolver.resolveActionNode(spinner).getActionNode());
        assertSame(checkbox, resolver.resolveActionNode(checkbox).getActionNode());
    }

    @Test
    public void resolvesSemanticContainerOnlyWhenItOwnsOneActionableDescendant() {
        FakeNode country = node("country-control").role("android.widget.Spinner").focusable();
        FakeNode container = node("country-container").text("Country").child(country);

        SemanticNodeResolver.Resolution<FakeNode> resolution = resolver.resolveActionNode(container);

        assertSame(country, resolution.getActionNode());
        assertTrue(resolution.usesActionBounds());

        container.child(node("second-action").clickable());
        assertNull(resolver.resolveActionNode(container));
    }

    @Test
    public void inputEligibilityRemainsRestrictedToWritableVisibleEnabledFields() {
        FakeNode writable = node("writable").editable("Day");
        FakeNode disabled = node("disabled").editable("Month");
        disabled.enabled = false;
        FakeNode button = node("button").clickable();

        assertTrue(resolver.isEligibleInput(writable));
        assertFalse(resolver.isEligibleInput(disabled));
        assertFalse(resolver.isEligibleInput(button));
    }

    private FakeNode node(String id) {
        return new FakeNode(id);
    }

    private static final class FakeNode {
        final String id;
        final List<FakeNode> children = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        FakeNode parent;
        FakeNode labelFor;
        FakeNode labeledBy;
        boolean visible = true;
        boolean enabled = true;
        boolean clickable;
        boolean editable;
        boolean supportsSetText;
        boolean checkable;
        boolean focusable;
        String className = "android.view.View";

        FakeNode(String id) {
            this.id = id;
        }

        FakeNode text(String value) {
            values.add(value);
            return this;
        }

        FakeNode editable(String hint) {
            editable = true;
            supportsSetText = true;
            focusable = true;
            className = "android.widget.EditText";
            if (hint != null) {
                values.add(hint);
            }
            return this;
        }

        FakeNode clickable() {
            clickable = true;
            return this;
        }

        FakeNode checkable() {
            checkable = true;
            focusable = true;
            className = "android.widget.CheckBox";
            return this;
        }

        FakeNode focusable() {
            focusable = true;
            return this;
        }

        FakeNode role(String value) {
            className = value;
            return this;
        }

        FakeNode labelFor(FakeNode value) {
            labelFor = value;
            value.labeledBy = this;
            return this;
        }

        FakeNode child(FakeNode value) {
            value.parent = this;
            children.add(value);
            return this;
        }
    }

    private static final class FakeAdapter implements SemanticNodeResolver.NodeAdapter<FakeNode> {
        @Override public FakeNode parent(FakeNode node) { return node.parent; }
        @Override public List<FakeNode> children(FakeNode node) { return node.children; }
        @Override public FakeNode labelFor(FakeNode node) { return node.labelFor; }
        @Override public FakeNode labeledBy(FakeNode node) { return node.labeledBy; }
        @Override public boolean isVisible(FakeNode node) { return node.visible; }
        @Override public boolean isEnabled(FakeNode node) { return node.enabled; }
        @Override public boolean isClickable(FakeNode node) { return node.clickable; }
        @Override public boolean supportsClick(FakeNode node) { return node.clickable; }
        @Override public boolean isEditable(FakeNode node) { return node.editable; }
        @Override public boolean supportsSetText(FakeNode node) { return node.supportsSetText; }
        @Override public boolean isCheckable(FakeNode node) { return node.checkable; }
        @Override public boolean isFocusable(FakeNode node) { return node.focusable; }
        @Override public String className(FakeNode node) { return node.className; }
        @Override public List<String> semanticValues(FakeNode node) { return node.values; }
    }
}
