package com.example.anroidaiassistant.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SelectionNumberParserTest {
    private final SelectionNumberParser parser = new SelectionNumberParser();

    @Test
    public void parsesDigitsAsZeroBasedIndexes() {
        assertEquals(Integer.valueOf(1), parser.parseSelectionNumber("2", 5));
    }

    @Test
    public void parsesTurkishAndEnglishOrdinals() {
        assertEquals(Integer.valueOf(0), parser.parseSelectionNumber("birinci", 5));
        assertEquals(Integer.valueOf(2), parser.parseSelectionNumber("third", 5));
    }

    @Test
    public void parsesArabicIndicDigits() {
        assertEquals(Integer.valueOf(3), parser.parseSelectionNumber("\u0664", 5));
    }

    @Test
    public void rejectsSelectionsOutsideChoiceRange() {
        assertNull(parser.parseSelectionNumber("five", 3));
    }

    @Test
    public void detectsCancelCommands() {
        assertTrue(parser.isCancelSelection("iptal"));
        assertTrue(parser.isCancelSelection("cancel"));
        assertTrue(parser.isCancelSelection("\u0627\u0644\u063a\u0627\u0621"));
        assertFalse(parser.isCancelSelection("ikinci"));
    }
}