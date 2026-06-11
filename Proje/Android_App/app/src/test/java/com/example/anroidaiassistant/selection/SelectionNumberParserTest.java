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
    public void parsesArabicArticleNumberWords() {
        assertEquals(Integer.valueOf(4), parser.parseSelectionNumber("\u062e\u0645\u0633\u0629", 10));
        assertEquals(Integer.valueOf(4), parser.parseSelectionNumber("\u0627\u0644\u062e\u0645\u0633\u0629", 10));
        assertEquals(Integer.valueOf(4), parser.parseSelectionNumber("\u0648\u0627\u0644\u062e\u0645\u0633\u0629", 10));
    }

    @Test
    public void parsesCompositeNumbersForGridSelections() {
        assertEquals(Integer.valueOf(24), parser.parseSelectionNumber("yirmi bes", 100));
        assertEquals(Integer.valueOf(24), parser.parseSelectionNumber("twenty five", 100));
        assertEquals(Integer.valueOf(24), parser.parseSelectionNumber("\u062e\u0645\u0633\u0629 \u0648\u0639\u0634\u0631\u064a\u0646", 100));
        assertEquals(Integer.valueOf(99), parser.parseSelectionNumber("yuz", 100));
    }

    @Test
    public void parsesTurkishSuffixedGridSelections() {
        assertEquals(Integer.valueOf(1), parser.parseSelectionNumber("ikiye bas", 100));
        assertEquals(Integer.valueOf(3), parser.parseSelectionNumber("dorde bas", 100));
        assertEquals(Integer.valueOf(4), parser.parseSelectionNumber("bese bas", 100));
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
