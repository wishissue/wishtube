package app.wishtube.data

import app.wishtube.domain.*
import app.wishtube.recommend.Topics
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryRepositoryTest {

    @Test
    fun testTopicsExtraction() {
        val video = Video(
            id = "peertube:host|1", source = SourceId.PEERTUBE, sourceVideoId = "host|1",
            title = "Kotlin and Compose tutorial", description = "Learn kotlin",
            creator = "Tech", creatorId = "tech@host", thumbnail = null,
            durationSec = 100, uploadDate = 0L, viewCount = 100,
            tags = listOf("kotlin", "compose"), categories = listOf("Programming"), sourceUrl = "https://example.com"
        )
        val topics = Topics.of(video)
        assertEquals(true, "kotlin" in topics)
        assertEquals(true, "compose" in topics)
    }
}
