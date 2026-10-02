package org.openvideo.aggregator.recommend

import kotlin.test.Test
import kotlin.test.assertTrue

class LanguageDetectorTest {
    @Test
    fun testDetectEnglish() {
        val title = "The quick brown fox jumps over the lazy dog"
        val desc = "This is a comprehensive guide to Android development."
        assertTrue(LanguageDetector.isPreferred(title, desc, "en"))
    }
}
