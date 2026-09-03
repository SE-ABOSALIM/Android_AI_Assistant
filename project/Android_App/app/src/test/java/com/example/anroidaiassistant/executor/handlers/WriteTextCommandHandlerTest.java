package com.example.anroidaiassistant.executor.handlers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WriteTextCommandHandlerTest {
    @Test
    public void joinsUppercaseSpelledLetters() {
        assertEquals(
                "CHRIIKI",
                WriteTextCommandHandler.normalizeSpelledSequence("C H R I I K I")
        );
    }

    @Test
    public void joinsLowercaseSpelledLettersWithoutChangingCase() {
        assertEquals(
                "chriiki",
                WriteTextCommandHandler.normalizeSpelledSequence("c h r i i k i")
        );
    }

    @Test
    public void preservesNormalPhraseSpacing() {
        assertEquals(
                "hello world",
                WriteTextCommandHandler.normalizeSpelledSequence("hello world")
        );
    }

    @Test
    public void preservesMultiWordNormalText() {
        assertEquals(
                "my full name",
                WriteTextCommandHandler.normalizeSpelledSequence("my full name")
        );
    }

    @Test
    public void preservesNormalCapitalizationAndPunctuation() {
        assertEquals(
                "Hello, World!",
                WriteTextCommandHandler.normalizeSpelledSequence("Hello, World!")
        );
    }
}
