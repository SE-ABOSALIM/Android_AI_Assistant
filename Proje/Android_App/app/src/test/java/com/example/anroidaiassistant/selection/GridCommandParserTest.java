package com.example.anroidaiassistant.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GridCommandParserTest {
    private final GridCommandParser parser = new GridCommandParser();

    @Test
    public void parsesGridResizeAndHideCommands() {
        assertEquals("smaller", parser.parseAction("smaller grid"));
        assertEquals("smaller", parser.parseAction("gridi kucult"));
        assertEquals("larger", parser.parseAction("bigger grid"));
        assertEquals("larger", parser.parseAction("gridi buyut"));
        assertEquals("hide", parser.parseAction("hide grid"));
        assertEquals("hide", parser.parseAction("gridi kapat"));
    }

    @Test
    public void ignoresPlainGridCellNumbers() {
        assertNull(parser.parseAction("twenty five"));
        assertNull(parser.parseAction("25"));
    }

    @Test
    public void onlyTreatsSelectionShapedSpeechAsGridCellSelection() {
        assertTrue(parser.isCellSelectionText("twenty five"));
        assertTrue(parser.isCellSelectionText("tap twenty five"));
        assertTrue(parser.isCellSelectionText("tap on five"));
        assertTrue(parser.isCellSelectionText("double tap five"));
        assertTrue(parser.isCellSelectionText("hold on five"));
        assertTrue(parser.isCellSelectionText("ikiye bas"));
        assertTrue(parser.isCellSelectionText("dorde bas"));
        assertTrue(parser.isCellSelectionText("\u0627\u0636\u063a\u0637 \u0639\u0644\u0649 \u062e\u0645\u0633\u0629"));
        assertTrue(parser.isCellSelectionText("\u0627\u0636\u063a\u0637 \u0645\u0637\u0648\u0644\u0627 \u0639\u0644\u0649 \u062e\u0645\u0633\u0629"));
        assertTrue(parser.isCellSelectionText("25"));

        assertFalse(parser.isCellSelectionText("tap home"));
        assertFalse(parser.isCellSelectionText("set timer for five minutes"));
        assertFalse(parser.isCellSelectionText("open instagram"));
        assertFalse(parser.isCellSelectionText("iptal"));
    }

    @Test
    public void parsesGridCellGestureAction() {
        assertEquals("tap", parser.parseCellGestureAction("tap five"));
        assertEquals("double_tap", parser.parseCellGestureAction("double tap five"));
        assertEquals("hold", parser.parseCellGestureAction("hold on five"));
        assertEquals("double_tap", parser.parseCellGestureAction("cift bas bes"));
        assertEquals("hold", parser.parseCellGestureAction("besi basili tut"));
        assertEquals("hold", parser.parseCellGestureAction("\u0627\u0636\u063a\u0637 \u0645\u0637\u0648\u0644\u0627 \u0639\u0644\u0649 \u062e\u0645\u0633\u0629"));
        assertEquals("hold", parser.parseCellGestureAction("\u0627\u0636\u063a\u0637 \u0628\u0627\u0633\u062a\u0645\u0631\u0627\u0631 \u0639\u0644\u0649 \u062e\u0645\u0633\u0629"));
    }
}
