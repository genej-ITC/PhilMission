package org.philmission.app

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private val phrase = Phrase("p1", "greeting", "Kumusta po?", "Hello. How are you?", "안녕하세요.", "꾸무스따 뽀?")

    @Test fun searchMatchesAnyLanguageIgnoringCase() {
        assertTrue(phrase.matches("kumusta"))
        assertTrue(phrase.matches("HELLO"))
        assertTrue(phrase.matches("안녕"))
        assertFalse(phrase.matches("없는말"))
        assertTrue(phrase.matches(""))
    }

    @Test fun languageSelectsText() {
        assertEquals("Kumusta po?", phrase.text("tl"))
        assertEquals("Hello. How are you?", phrase.text("en"))
    }

    @Test fun contactValidation() {
        assertNotNull(validateContact("", "0912"))
        assertNotNull(validateContact("김", ""))
        assertNotNull(validateContact("김", "abc"))
        assertNotNull(validateContact("김", "12"))
        assertNull(validateContact("김", "+63 912-345 6789"))
    }

    @Test fun songChordDetection() {
        val plain = Song("s", "t", "t", "en", "c", "x", listOf(SongLine(emptyList(), "a", "", "b")))
        val chords = plain.copy(lines = listOf(SongLine(listOf("G"), "a", "", "b")))
        assertFalse(plain.hasChords)
        assertTrue(chords.hasChords)
    }
}
