package app.wishtube.data

import kotlinx.serialization.json.Json
import app.wishtube.recommend.RecWeights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProfileExportTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Test
    fun testProfileExportImportRoundTrip() {
        val export = ProfileExport(
            version = 1,
            name = "Test Profile",
            videos = emptyList(),
            entries = listOf(LibraryEntry(1, "vid1", Kind.HISTORY, 123456L, 1000L, 5000L)),
            playlists = listOf(ExpPlaylist("Favorites", listOf("vid1"))),
            notes = listOf(NoteEntity(1, "vid1", "Great video")),
            follows = listOf(FollowEntity(1, "creator1", "Creator Name", "PEERTUBE", "cId")),
            hiddenCreators = listOf(HiddenCreator(1, "badCreator")),
            hiddenTopics = listOf(HiddenTopic(1, "spam")),
            searches = listOf(SearchEntity(1, "kotlin", 12345L)),
            weights = RecWeights(topic = 0.9f)
        )

        val serialized = json.encodeToString(ProfileExport.serializer(), export)
        assertNotNull(serialized)

        val deserialized = json.decodeFromString(ProfileExport.serializer(), serialized)
        assertEquals(export.name, deserialized.name)
        assertEquals(export.version, deserialized.version)
        assertEquals(export.entries.size, deserialized.entries.size)
        assertEquals(export.weights.topic, deserialized.weights.topic)
        assertEquals(export.playlists[0].name, deserialized.playlists[0].name)
    }
}
