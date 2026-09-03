package com.example.anroidaiassistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CloseAppBlockingModalDetectorTest {
    private static final String PACKAGE = "package.a";
    private final CloseAppWindowSnapshot pre = window(10, 0, 0, 1080, 2400);
    private final CloseAppWindowSnapshot dialog = window(11, 28, 912, 1052, 1425);

    @Test
    public void confirmedSeparateDialogWindow_detectedWithoutWindowCountGrowth() {
        assertTrue(detect(pre, dialog));
    }

    @Test
    public void relativeGeometry_worksAtDifferentScaleAndOffset() {
        assertTrue(detect(window(20, 100, 200, 2100, 4200),
                window(21, 200, 1500, 2000, 2300)));
    }

    @Test
    public void newFullScreenActivity_notModal() {
        assertFalse(detect(pre, window(11, 0, 0, 1080, 2400)));
    }

    @Test
    public void smallInsetActivity_notModal() {
        assertFalse(detect(pre, window(11, 20, 40, 1060, 2360)));
    }

    @Test
    public void sameWindowEvenWithSmallerBounds_notModal() {
        assertFalse(detect(pre, window(10, 28, 912, 1052, 1425)));
    }

    @Test
    public void zeroOrInvertedBounds_notModal() {
        assertFalse(detect(pre, window(11, 0, 0, 0, 0)));
        assertFalse(detect(pre, window(11, 100, 200, 50, 150)));
        assertFalse(detect(window(10, 0, 0, 0, 0), dialog));
    }

    @Test
    public void missingSnapshot_notModal() {
        assertFalse(detect(null, dialog));
        assertFalse(detect(pre, null));
    }

    @Test
    public void differentOrMissingPackage_notModal() {
        assertFalse(detect(pre, new CloseAppWindowSnapshot("package.b", 11,
                true, true, true, 28, 912, 1052, 1425)));
        assertFalse(detect(new CloseAppWindowSnapshot("package.b", 10,
                true, true, true, 0, 0, 1080, 2400), dialog));
        assertFalse(CloseAppBlockingModalDetector.isBlockingModal(null, pre, dialog));
        assertFalse(detect(pre, new CloseAppWindowSnapshot(null, 11,
                true, true, true, 28, 912, 1052, 1425)));
    }

    @Test
    public void unknownWindowIdentity_notModal() {
        assertFalse(detect(window(-1, 0, 0, 1080, 2400), dialog));
        assertFalse(detect(pre, window(-1, 28, 912, 1052, 1425)));
    }

    @Test
    public void nonApplicationWindow_notModal() {
        assertInvalidWindowFlags(false, true, true);
    }

    @Test
    public void inactiveWindow_notModal() {
        assertInvalidWindowFlags(true, false, true);
    }

    @Test
    public void unfocusedWindow_notModal() {
        assertInvalidWindowFlags(true, true, false);
    }

    @Test
    public void smallWindowOutsidePreviousBounds_notModal() {
        assertFalse(detect(pre, window(11, 1100, 912, 1500, 1425)));
    }

    @Test
    public void majorityAreaWindow_notModal() {
        assertFalse(detect(pre, window(11, 10, 400, 1070, 2000)));
    }

    private void assertInvalidWindowFlags(boolean application, boolean active, boolean focused) {
        assertFalse(detect(pre, new CloseAppWindowSnapshot(PACKAGE, 11,
                application, active, focused, 28, 912, 1052, 1425)));
        assertFalse(detect(new CloseAppWindowSnapshot(PACKAGE, 10,
                application, active, focused, 0, 0, 1080, 2400), dialog));
    }

    private static boolean detect(CloseAppWindowSnapshot pre, CloseAppWindowSnapshot post) {
        return CloseAppBlockingModalDetector.isBlockingModal(PACKAGE, pre, post);
    }

    private static CloseAppWindowSnapshot window(int id, int left, int top, int right, int bottom) {
        return new CloseAppWindowSnapshot(PACKAGE, id, true, true, true, left, top, right, bottom);
    }
}
