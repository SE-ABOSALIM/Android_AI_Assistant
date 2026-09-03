package com.example.anroidaiassistant.accessibility.semantic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.example.anroidaiassistant.accessibility.click.ClickTextMatcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InputTargetSelectorTest {
    private final InputTargetSelector<Field> selector = new InputTargetSelector<>(new ClickTextMatcher());

    @Test
    public void namedTargetRestrictsSelectionToMatchingInput() {
        Field day = new Field("Day");
        Field month = new Field("Month");

        List<Field> matches = selector.selectMatches(
                Arrays.asList(day, month),
                Collections.singletonList("day"),
                field -> Collections.singletonList(field.label)
        );

        assertEquals(1, matches.size());
        assertSame(day, matches.get(0));
    }

    @Test
    public void duplicateNamedMatchesRemainAmbiguous() {
        Field first = new Field("Day");
        Field second = new Field("Day");

        List<Field> matches = selector.selectMatches(
                Arrays.asList(first, second),
                Collections.singletonList("day"),
                field -> Collections.singletonList(field.label)
        );

        assertEquals(Arrays.asList(first, second), matches);
    }

    @Test
    public void missingExplicitTargetReturnsNoInputs() {
        List<Field> matches = selector.selectMatches(
                Arrays.asList(new Field("Day"), new Field("Month")),
                Collections.singletonList("qwertyzxcv"),
                field -> Collections.singletonList(field.label)
        );

        assertEquals(Collections.emptyList(), matches);
    }

    private static final class Field {
        final String label;

        Field(String label) {
            this.label = label;
        }
    }
}
