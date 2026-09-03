package com.example.anroidaiassistant.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenLabelEligibilityTest {
    @Test
    public void rejectsLargeUnlabeledClickableRootContainer() {
        assertFalse(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(0, 0, 1080, 1800)
                .screen(1080, 1920)
                .root()
                .children(6)
                .role("android.webkit.WebView")));
    }

    @Test
    public void rejectsLargeStructuralContainerWhileKeepingMeaningfulChildrenEligible() {
        ScreenLabelEligibility.NodeFacts parent = baseNode()
                .bounds(0, 0, 1080, 1800)
                .screen(1080, 1920)
                .children(4)
                .role("android.widget.FrameLayout");
        ScreenLabelEligibility.NodeFacts child = baseNode()
                .bounds(80, 1400, 1000, 1500)
                .screen(1080, 1920)
                .text("Continue")
                .role("android.widget.Button");

        assertFalse(ScreenLabelEligibility.isUsable(parent));
        assertTrue(ScreenLabelEligibility.isUsable(child));
    }

    @Test
    public void keepsNormalButton() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 220, 100)
                .text("Next")
                .role("android.widget.Button")));
    }

    @Test
    public void keepsClickableTextLink() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 420, 80)
                .text("Forgot password")
                .role("android.widget.TextView")));
    }

    @Test
    public void keepsCheckableControl() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 100, 100)
                .checkable()
                .role("android.widget.CheckBox")));
    }

    @Test
    public void keepsEditableInput() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 600, 120)
                .editable()
                .hint("Name")
                .role("android.widget.EditText")));
    }

    @Test
    public void keepsDropdownControl() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 600, 120)
                .role("android.widget.Spinner")));
    }

    @Test
    public void keepsIconButtonWithMeaningfulDescription() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 100, 100)
                .contentDescription("Back")
                .role("android.widget.ImageButton")));
    }

    @Test
    public void keepsMeaningfulClickableCardEvenWhenItIsLarge() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(0, 0, 1080, 1800)
                .screen(1080, 1920)
                .children(2)
                .text("Open account settings")
                .role("android.widget.FrameLayout")));
    }

    @Test
    public void keepsSmallUnlabeledClickableControlForIconFallbackCompatibility() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 68, 68)
                .role("android.view.View")));
    }

    @Test
    public void rejectsLargeContainerWithOnlyGenericStructuralResourceId() {
        assertFalse(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(0, 0, 1080, 1800)
                .screen(1080, 1920)
                .children(3)
                .viewId("com.example:id/main_content_container")
                .role("android.widget.FrameLayout")));
    }

    @Test
    public void rejectsPageSizedStructuralNodeWhenResourceIdIsItsOnlySemanticEvidence() {
        ScreenLabelEligibility.NodeFacts parent = baseNode()
                .bounds(0, 210, 1084, 2276)
                .screen(1084, 2276)
                .children(1)
                .viewId("mount_0_0_/O")
                .role("android.view.View");
        ScreenLabelEligibility.NodeFacts meaningfulChild = baseNode()
                .bounds(80, 1800, 1000, 1900)
                .screen(1084, 2276)
                .text("Continue")
                .role("android.widget.Button");

        assertFalse(ScreenLabelEligibility.isUsable(parent));
        assertTrue(ScreenLabelEligibility.isUsable(meaningfulChild));
    }

    @Test
    public void keepsNormalControlWithMeaningfulResourceId() {
        assertTrue(ScreenLabelEligibility.isUsable(baseNode()
                .bounds(20, 20, 220, 100)
                .viewId("com.example:id/continue_action")
                .role("android.view.View")));
    }

    private ScreenLabelEligibility.NodeFacts baseNode() {
        return new ScreenLabelEligibility.NodeFacts()
                .visible()
                .enabled()
                .clickable()
                .supportsClick()
                .bounds(0, 0, 120, 60)
                .screen(1080, 1920)
                .hasParent();
    }
}
