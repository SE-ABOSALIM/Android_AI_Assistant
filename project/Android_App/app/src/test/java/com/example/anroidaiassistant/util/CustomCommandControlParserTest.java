package com.example.anroidaiassistant.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomCommandControlParserTest {
    @Test
    public void recognizesExplicitCancelCommandsInAllSupportedLanguages() {
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("cancel custom command"));
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("stop the custom command"));
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("özel komutu iptal et"));
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("komut akışını durdur"));
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("إلغاء الأمر المخصص"));
        assertTrue(CustomCommandControlParser.isExplicitCancelCommand("أوقف الأمر الخاص"));
    }

    @Test
    public void recognizesShortCancelActionsForAnActiveFlow() {
        assertTrue(CustomCommandControlParser.isCancelAction("cancel"));
        assertTrue(CustomCommandControlParser.isCancelAction("iptal"));
        assertTrue(CustomCommandControlParser.isCancelAction("إلغاء"));
    }

    @Test
    public void doesNotTreatUnrelatedCommandsAsCustomCommandCancellation() {
        assertFalse(CustomCommandControlParser.isExplicitCancelCommand("stop listening"));
        assertFalse(CustomCommandControlParser.isExplicitCancelCommand("close the application"));
        assertFalse(CustomCommandControlParser.isExplicitCancelCommand("cancel"));
        assertFalse(CustomCommandControlParser.isCancelAction("cancel download"));
    }
}
