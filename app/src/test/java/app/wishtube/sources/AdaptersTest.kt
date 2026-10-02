package app.wishtube.sources

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import app.wishtube.domain.SearchSort
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdaptersTest {

    private lateinit var server: MockWebServer
    private val odyseeAdapter = OdyseeAdapter()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testOdyseeUrlRecognition() = runTest {
        val v = odyseeAdapter.resolveUrl("https://youtube.com/watch?v=123")
        assertNull(v)
    }

    @Test
    fun testPeerTubeUrlRecognition() = runTest {
        val pt = PeerTubeAdapter(MockSharedPreferences())
        val v = pt.resolveUrl("https://youtube.com/watch?v=123")
        assertNull(v)
    }

    @Test
    fun testOptInLiveNetwork() = runTest {
        if (System.getProperty("enableLiveTests") != "true") {
            return@runTest
        }
        val pt = PeerTubeAdapter(MockSharedPreferences())
        val videos = pt.search("nature", 0, SearchSort.RELEVANCE)
        assertNotNull(videos)
    }
}

class MockSharedPreferences : SharedPreferences {
    override fun edit(): SharedPreferences.Editor = MockEditor()
    override fun getAll(): Map<String, Any?> = emptyMap()
    override fun getString(key: String, defValue: String?): String? = defValue
    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? = defValue
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun contains(key: String): Boolean = false
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

class MockEditor : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?) = this
    override fun putStringSet(key: String?, values: MutableSet<String>?) = this
    override fun putInt(key: String?, value: Int) = this
    override fun putLong(key: String?, value: Long) = this
    override fun putFloat(key: String?, value: Float) = this
    override fun putBoolean(key: String?, value: Boolean) = this
    override fun remove(key: String?) = this
    override fun clear() = this
    override fun commit(): Boolean = true
    override fun apply() {}
}
