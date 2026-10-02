package app.wishtube.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class RoomDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.dao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertAndGetVideo() = runBlocking {
        val cached = CachedVideo(
            id = "peertube:test|1", source = "PEERTUBE", sourceVideoId = "test|1",
            title = "Test Video", description = "Desc", creator = "Creator", creatorId = "c1",
            thumbnail = null, durationSec = 100, uploadDate = 123456L, viewCount = 10,
            tags = "tag1", categories = "cat1", sourceUrl = "https://example.com", fetchedAt = System.currentTimeMillis()
        )
        dao.upsertVideos(listOf(cached))
        val retrieved = dao.video("peertube:test|1")
        assertNotNull(retrieved)
        assertEquals("Test Video", retrieved.title)
    }

    @Test
    fun testProfiles() = runBlocking {
        val pId = dao.insertProfile(ProfileEntity(name = "Default"))
        val profiles = dao.profilesNow()
        assertEquals(1, profiles.size)
        assertEquals("Default", profiles[0].name)
    }
}
