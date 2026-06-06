package com.example.anroidaiassistant.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextNormalizerTest {
    @Test
    public void normalizesArabicLetterVariants() {
        assertEquals(
                TextNormalizer.normalizeAsciiText("\u0627\u0644\u0635\u0641\u062D\u0629 \u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0647"),
                TextNormalizer.normalizeAsciiText("\u0627\u0644\u0635\u0641\u062D\u0629 \u0627\u0644\u0631\u0626\u064A\u0633\u064A\u0629")
        );
    }
}
