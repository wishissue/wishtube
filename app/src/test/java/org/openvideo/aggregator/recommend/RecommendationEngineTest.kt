package org.openvideo.aggregator.recommend

import org.openvideo.aggregator.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecommendationEngineTest {

    private val sampleVideo1 = Video(
        id = "peertube:host|1", source = SourceId.PEERTUBE, sourceVideoId = "host|1",
        title = "Kotlin Coroutines Guide", description = "Learn coroutines",
        creator = "TechChannel", creatorId = "TechChannel@host", thumbnail = null,
        durationSec = 600, uploadDate = System.currentTimeMillis() - 1000,
        viewCount = 1000, tags = listOf("kotlin", "programming"), categories = listOf("Tech"), sourceUrl = "https://example.com/1"
    )

    private val sampleVideo2 = Video(
        id = "odysee:2", source = SourceId.ODYSEE, sourceVideoId = "name|2|sd123",
        title = "Compose UI Animation", description = "Animate UI",
        creator = "DesignChannel", creatorId = "design_claim_id", thumbnail = null,
        durationSec = 120, uploadDate = System.currentTimeMillis() - 50000,
        viewCount = 500, tags = listOf("compose", "ui"), categories = listOf("Design"), sourceUrl = "https://example.com/2"
    )

    @Test
    fun testFeedModes() {
        val candidates = listOf(sampleVideo1, sampleVideo2)
        val sig = ProfileSignals()
        val w = RecWeights()

        for (mode in FeedMode.entries) {
            val ranked = RecommendationEngine.rank(candidates, sig, w, mode)
            assertNotNull(ranked)
        }
    }

    @Test
    fun testHiddenCreatorsAndTopics() {
        val candidates = listOf(sampleVideo1, sampleVideo2)
        val sig = ProfileSignals(
            hiddenCreators = setOf(sampleVideo1.creatorKey),
            hiddenTopics = setOf("compose")
        )
        val ranked = RecommendationEngine.rank(candidates, sig, RecWeights(), FeedMode.FOR_YOU)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun testNotInterested() {
        val candidates = listOf(sampleVideo1, sampleVideo2)
        val sig = ProfileSignals(
            notInterested = setOf("odysee:2")
        )
        val ranked = RecommendationEngine.rank(candidates, sig, RecWeights(), FeedMode.FOR_YOU)
        assertEquals(1, ranked.size)
        assertEquals("peertube:host|1", ranked[0].video.id)
    }
}
