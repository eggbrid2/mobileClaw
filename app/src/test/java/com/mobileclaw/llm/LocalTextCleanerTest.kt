package com.mobileclaw.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTextCleanerTest {
    @Test
    fun localStreamingCleanerKeepsSpaceOnlyTokensInBuffer() {
        val cleaner = LocalStreamingTextCleaner()

        assertEquals("Hello", cleaner.append("Hello"))
        assertEquals("", cleaner.append(" "))
        assertEquals(" world", cleaner.append("world"))
        assertEquals("Hello world", cleaner.finalText())
    }

    @Test
    fun localStreamingCleanerDecodesTokenizerWordBoundaries() {
        val cleaner = LocalStreamingTextCleaner()

        assertEquals("Hello", cleaner.append("▁Hello"))
        assertEquals(" world", cleaner.append("▁world"))
        assertEquals(" from", cleaner.append("Ġfrom"))
        assertEquals(" local", cleaner.append("▁local"))
        assertEquals("Hello world from local", cleaner.finalText())
    }

    @Test
    fun localStreamingCleanerRepairsMissingEnglishTokenBoundaries() {
        val cleaner = LocalStreamingTextCleaner()

        assertEquals("Hi", cleaner.append("Hi"))
        assertEquals("!", cleaner.append("!"))
        assertEquals(" How", cleaner.append("How"))
        assertEquals(" can", cleaner.append("can"))
        assertEquals(" I", cleaner.append("I"))
        assertEquals(" help", cleaner.append("help"))
        assertEquals(" you", cleaner.append("you"))
        assertEquals(" today", cleaner.append("today"))
        assertEquals("?", cleaner.append("?"))
        assertEquals("Hi! How can I help you today?", cleaner.finalText())
    }

    @Test
    fun mergesTwoEnglishTokenLinesWithSpaceForStreamingStability() {
        val raw = """
            <start_of_turn>model
            This
            is
        """.trimIndent()

        assertEquals(
            "This is",
            raw.cleanLocalGeneratedText(),
        )
    }

    @Test
    fun preservesSpacesWhenLocalModelStreamsEnglishWordsOnSeparateLines() {
        val raw = """
            <start_of_turn>model
            This
            is
            a
            local
            model
            reply
            .
        """.trimIndent()

        assertEquals(
            "This is a local model reply.",
            raw.cleanLocalGeneratedText(),
        )
    }

    @Test
    fun keepsChineseFragmentMergingWithoutExtraSpaces() {
        val raw = """
            <start_of_turn>model
            你好
            ，
            我
            可以
            帮你
            。
        """.trimIndent()

        assertEquals(
            "你好，我可以帮你。",
            raw.cleanLocalGeneratedText(),
        )
    }
}
