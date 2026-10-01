package org.openvideo.aggregator.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChaptersTest {

    @Test
    fun testParseChaptersValid() {
        val desc = """
            Welcome to the video.
            0:00 Introduction
            02:15 Setup and Installation
            05:30 Building the UI
            10:00 Conclusion
        """.trimIndent()

        val chapters = parseChapters(desc)
        assertEquals(4, chapters.size)
        assertEquals(0, chapters[0].startSec)
        assertEquals("Introduction", chapters[0].title)
        assertEquals(135, chapters[1].startSec)
        assertEquals("Setup and Installation", chapters[1].title)
    }

    @Test
    fun testParseChaptersInvalidTooFew() {
        val desc = """
            0:00 Intro
            01:00 Outro
        """.trimIndent()
        val chapters = parseChapters(desc)
        assertTrue(chapters.isEmpty())
    }
}
