package app.wishtube.sources

import android.content.SharedPreferences
import app.wishtube.domain.SourceCapabilities
import app.wishtube.domain.SourceId
import app.wishtube.domain.Video
import app.wishtube.domain.SearchSort
import app.wishtube.domain.FeedSort
import app.wishtube.domain.CreatorInfo
import app.wishtube.domain.StreamInfo
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class HttpAndSourceManagerTest {

    @Test
    fun testPeerTubeInstanceValidation() {
        val sm = SourceManager(FakeSharedPreferences())

        // Accepted URLs
        assertNull(sm.validatePeerTubeInstanceUrl("https://framatube.org"))
        assertNull(sm.validatePeerTubeInstanceUrl("https://video.blender.org"))
        assertNull(sm.validatePeerTubeInstanceUrl("peertube.social"))

        // Rejected URLs
        assertNotNull(sm.validatePeerTubeInstanceUrl("http://framatube.org"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://user:pass@framatube.org"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("localhost"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://localhost"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://myhost.local"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://127.0.0.1"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://10.0.0.1"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://192.168.1.1"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://172.16.0.1"))
        assertNotNull(sm.validatePeerTubeInstanceUrl("https://169.254.1.1"))
    }

    @Test
    fun testResponseBodySizeCap() {
        val largeBody = "x".repeat(6 * 1024 * 1024)
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(largeBody.toResponseBody())
            .build()

        assertFailsWith<IOException> {
            Http.readResponseBodyString(response)
        }
    }

    @Test
    fun testFailingAdapterIsolation() = runTest {
        val workingAdapter = object : VideoSource {
            override val id = SourceId.PEERTUBE
            override val capabilities = SourceCapabilities()
            override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> = listOf(
                Video(id = "peertube:1", source = SourceId.PEERTUBE, sourceVideoId = "1", title = "Working", description = "", creator = "c", creatorId = "c", thumbnail = null, durationSec = 10, uploadDate = 0, viewCount = 0, tags = emptyList(), categories = emptyList(), sourceUrl = "")
            )
            override suspend fun feed(page: Int, sort: FeedSort): List<Video> = emptyList()
            override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> = emptyList()
            override suspend fun creatorInfo(creatorId: String): CreatorInfo? = null
            override suspend fun resolveStream(video: Video): StreamInfo = StreamInfo(urls = listOf("http://ex.com"), isHls = false)
            override suspend fun resolveUrl(url: String): Video? = null
        }

        val failingAdapter = object : VideoSource {
            override val id = SourceId.ODYSEE
            override val capabilities = SourceCapabilities()
            override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> {
                throw IOException("Network error on Odysee")
            }
            override suspend fun feed(page: Int, sort: FeedSort): List<Video> = emptyList()
            override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> = emptyList()
            override suspend fun creatorInfo(creatorId: String): CreatorInfo? = null
            override suspend fun resolveStream(video: Video): StreamInfo = StreamInfo(urls = listOf("http://ex.com"), isHls = false)
            override suspend fun resolveUrl(url: String): Video? = null
        }

        val sm = SourceManager(FakeSharedPreferences())
        // Replace sources with test adapters
        val field = SourceManager::class.java.getDeclaredField("sources")
        field.isAccessible = true
        field.set(sm, listOf(workingAdapter, failingAdapter))

        val result = sm.query { it.search("test", 0, SearchSort.RELEVANCE) }
        assertEquals(1, result.videos.size)
        assertEquals("peertube:1", result.videos[0].id)
        assertTrue(result.errors.containsKey(SourceId.ODYSEE))
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    override fun getAll(): Map<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { map[key!!] = value; return this }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor { map[key!!] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { map[key!!] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { map[key!!] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { map[key!!] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { map[key!!] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { map.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
