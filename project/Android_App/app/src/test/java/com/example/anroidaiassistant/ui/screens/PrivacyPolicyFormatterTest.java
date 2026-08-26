package com.example.anroidaiassistant.ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrivacyPolicyFormatterTest {
    @Test
    public void plainText_preservesDocumentHierarchyAndListStructure() {
        String markdown = "# Policy\n\n"
                + "## Collection\n\n"
                + "This is **important** with `code` and [details](https://example.com).\n\n"
                + "- First item\n"
                + "  continued\n"
                + "- Second item\n\n"
                + "1. First step\n"
                + "2. Second step";

        String readable = PrivacyPolicyFormatter.plainText(markdown, false);

        assertEquals(
                "Policy\n\nCollection\nThis is important with code and details.\n\n"
                        + "•  First item continued\n•  Second item\n\n"
                        + "1.  First step\n2.  Second step",
                readable
        );
        assertFalse(readable.contains("**"));
        assertFalse(readable.contains("https://"));
    }

    @Test
    public void plainText_canOmitDocumentTitleWhenDialogAlreadyHasTitle() {
        String readable = PrivacyPolicyFormatter.plainText(
                "# Privacy Policy\n\n## Information\n\nBody",
                true
        );

        assertTrue(readable.startsWith("Information"));
        assertFalse(readable.contains("Privacy Policy"));
    }
}
