package app.wishtube.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import app.wishtube.domain.SourceId
import app.wishtube.domain.Video

object Kind {
    const val HISTORY = "HISTORY"; const val WATCH_LATER = "WATCH_LATER"; const val SAVED = "SAVED"
    const val LIKED = "LIKED"; const val NOT_INTERESTED = "NOT_INTERESTED"
}

object DlStatus { const val RUNNING = "RUNNING"; const val DONE = "DONE"; const val FAILED = "FAILED" }

@Entity
data class ProfileEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity
@Serializable
data class CachedVideo(
    @PrimaryKey val id: String, val source: String, val sourceVideoId: String, val title: String,
    val description: String, val creator: String, val creatorId: String, val thumbnail: String?,
    val durationSec: Long, val uploadDate: Long, val viewCount: Long?, val tags: String,
    val categories: String, val sourceUrl: String, val fetchedAt: Long,
)

@Entity(primaryKeys = ["profileId", "videoId", "kind"])
@Serializable
data class LibraryEntry(
    val profileId: Long, val videoId: String, val kind: String, val addedAt: Long,
    val progressMs: Long = 0, val durationMs: Long = 0,
)

@Entity
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val profileId: Long, val name: String)

@Entity(primaryKeys = ["playlistId", "videoId"])
data class PlaylistItem(val playlistId: Long, val videoId: String, val addedAt: Long)

@Entity(primaryKeys = ["profileId", "videoId"])
@Serializable
data class NoteEntity(val profileId: Long, val videoId: String, val text: String)

@Entity(primaryKeys = ["profileId", "creatorKey"])
@Serializable
data class FollowEntity(val profileId: Long, val creatorKey: String, val name: String, val source: String, val creatorId: String)

@Entity(primaryKeys = ["profileId", "creatorKey"])
@Serializable
data class HiddenCreator(val profileId: Long, val creatorKey: String)

@Entity(primaryKeys = ["profileId", "topic"])
@Serializable
data class HiddenTopic(val profileId: Long, val topic: String)

@Entity(primaryKeys = ["profileId", "term"])
@Serializable
data class SearchEntity(val profileId: Long, val term: String, val at: Long)

@Entity
data class DownloadEntity(
    @PrimaryKey val videoId: String, val path: String, val status: String,
    val bytes: Long, val total: Long, val createdAt: Long,
)

data class NoteRow(@Embedded val video: CachedVideo, val noteText: String)

private const val SEP = "\u001F"

fun Video.toCached(now: Long) = CachedVideo(
    id, source.name, sourceVideoId, title, description.take(4000), creator, creatorId, thumbnail,
    durationSec, uploadDate, viewCount, tags.joinToString(SEP), categories.joinToString(SEP), sourceUrl, now)

fun CachedVideo.toVideo() = Video(
    id, SourceId.valueOf(source), sourceVideoId, title, description, creator, creatorId, thumbnail,
    durationSec, uploadDate, viewCount, tags.split(SEP).filter { it.isNotEmpty() },
    categories.split(SEP).filter { it.isNotEmpty() }, sourceUrl)

@Dao
interface AppDao {
    // video cache
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVideos(v: List<CachedVideo>)
    @Query("SELECT * FROM CachedVideo WHERE id = :id") suspend fun video(id: String): CachedVideo?
    @Query("SELECT * FROM CachedVideo WHERE id IN (:ids)") suspend fun videosByIds(ids: List<String>): List<CachedVideo>
    @Query("SELECT * FROM CachedVideo ORDER BY fetchedAt DESC LIMIT :n") suspend fun recentCached(n: Int): List<CachedVideo>
    @Query("DELETE FROM CachedVideo WHERE id NOT IN (SELECT videoId FROM LibraryEntry UNION SELECT videoId FROM PlaylistItem UNION SELECT videoId FROM DownloadEntity UNION SELECT videoId FROM NoteEntity)")
    suspend fun clearCache()

    // profiles
    @Query("SELECT * FROM ProfileEntity ORDER BY id") fun profiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM ProfileEntity ORDER BY id") suspend fun profilesNow(): List<ProfileEntity>
    @Query("SELECT COUNT(*) FROM ProfileEntity") suspend fun profileCount(): Int
    @Insert suspend fun insertProfile(p: ProfileEntity): Long
    @Query("DELETE FROM ProfileEntity WHERE id = :p") suspend fun deleteProfile(p: Long)
    @Query("DELETE FROM LibraryEntry WHERE profileId = :p") suspend fun deleteEntriesOf(p: Long)
    @Query("DELETE FROM PlaylistItem WHERE playlistId IN (SELECT id FROM PlaylistEntity WHERE profileId = :p)") suspend fun deletePlaylistItemsOf(p: Long)
    @Query("DELETE FROM PlaylistEntity WHERE profileId = :p") suspend fun deletePlaylistsOf(p: Long)
    @Query("DELETE FROM NoteEntity WHERE profileId = :p") suspend fun deleteNotesOf(p: Long)
    @Query("DELETE FROM FollowEntity WHERE profileId = :p") suspend fun deleteFollowsOf(p: Long)
    @Query("DELETE FROM HiddenCreator WHERE profileId = :p") suspend fun deleteHiddenCreatorsOf(p: Long)
    @Query("DELETE FROM HiddenTopic WHERE profileId = :p") suspend fun deleteHiddenTopicsOf(p: Long)
    @Query("DELETE FROM SearchEntity WHERE profileId = :p") suspend fun deleteSearchesOf(p: Long)

    // library entries
    @Query("SELECT v.* FROM CachedVideo v JOIN LibraryEntry e ON v.id = e.videoId WHERE e.profileId = :p AND e.kind = :kind ORDER BY e.addedAt DESC")
    fun videosOf(p: Long, kind: String): Flow<List<CachedVideo>>
    @Query("SELECT * FROM LibraryEntry WHERE profileId = :p AND videoId = :v") fun entriesFor(p: Long, v: String): Flow<List<LibraryEntry>>
    @Query("SELECT * FROM LibraryEntry WHERE profileId = :p ORDER BY addedAt DESC LIMIT 800") suspend fun entriesNow(p: Long): List<LibraryEntry>
    @Query("SELECT * FROM LibraryEntry WHERE profileId = :p") suspend fun entriesAll(p: Long): List<LibraryEntry>
    @Query("SELECT * FROM LibraryEntry WHERE profileId = :p AND videoId = :v AND kind = 'HISTORY'") suspend fun historyEntry(p: Long, v: String): LibraryEntry?
    @Query("SELECT COUNT(*) FROM LibraryEntry WHERE profileId = :p AND videoId = :v AND kind = :kind") suspend fun hasEntry(p: Long, v: String, kind: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEntry(e: LibraryEntry)
    @Query("DELETE FROM LibraryEntry WHERE profileId = :p AND videoId = :v AND kind = :kind") suspend fun removeEntry(p: Long, v: String, kind: String)
    @Query("DELETE FROM LibraryEntry WHERE profileId = :p AND kind = :kind") suspend fun clearKind(p: Long, kind: String)

    // playlists
    @Query("SELECT * FROM PlaylistEntity WHERE profileId = :p ORDER BY id DESC") fun playlists(p: Long): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM PlaylistEntity WHERE profileId = :p ORDER BY id") suspend fun playlistsNow(p: Long): List<PlaylistEntity>
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("DELETE FROM PlaylistEntity WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM PlaylistItem WHERE playlistId = :id") suspend fun deletePlaylistItems(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addToPlaylist(i: PlaylistItem)
    @Query("SELECT * FROM PlaylistItem WHERE playlistId = :id ORDER BY addedAt") suspend fun playlistItemsNow(id: Long): List<PlaylistItem>
    @Query("SELECT v.* FROM CachedVideo v JOIN PlaylistItem i ON v.id = i.videoId WHERE i.playlistId = :id ORDER BY i.addedAt DESC")
    fun playlistVideos(id: Long): Flow<List<CachedVideo>>

    // notes
    @Query("SELECT text FROM NoteEntity WHERE profileId = :p AND videoId = :v") fun note(p: Long, v: String): Flow<String?>
    @Query("SELECT v.*, n.text AS noteText FROM CachedVideo v JOIN NoteEntity n ON v.id = n.videoId WHERE n.profileId = :p")
    fun notesWithVideos(p: Long): Flow<List<NoteRow>>
    @Query("SELECT * FROM NoteEntity WHERE profileId = :p") suspend fun notesNow(p: Long): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNote(n: NoteEntity)
    @Query("DELETE FROM NoteEntity WHERE profileId = :p AND videoId = :v") suspend fun deleteNote(p: Long, v: String)

    // follows / hidden
    @Query("SELECT * FROM FollowEntity WHERE profileId = :p") fun follows(p: Long): Flow<List<FollowEntity>>
    @Query("SELECT * FROM FollowEntity WHERE profileId = :p") suspend fun followsNow(p: Long): List<FollowEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFollow(f: FollowEntity)
    @Query("DELETE FROM FollowEntity WHERE profileId = :p AND creatorKey = :k") suspend fun removeFollow(p: Long, k: String)
    @Query("SELECT * FROM HiddenCreator WHERE profileId = :p") fun hiddenCreators(p: Long): Flow<List<HiddenCreator>>
    @Query("SELECT * FROM HiddenCreator WHERE profileId = :p") suspend fun hiddenCreatorsNow(p: Long): List<HiddenCreator>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHiddenCreator(h: HiddenCreator)
    @Query("SELECT * FROM HiddenTopic WHERE profileId = :p") suspend fun hiddenTopicsNow(p: Long): List<HiddenTopic>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHiddenTopic(h: HiddenTopic)

    // searches
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSearch(s: SearchEntity)
    @Query("SELECT * FROM SearchEntity WHERE profileId = :p ORDER BY at DESC LIMIT 12") fun recentSearches(p: Long): Flow<List<SearchEntity>>
    @Query("SELECT * FROM SearchEntity WHERE profileId = :p ORDER BY at DESC LIMIT 60") suspend fun searchesNow(p: Long): List<SearchEntity>

    // downloads
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDownload(d: DownloadEntity)
    @Query("SELECT * FROM DownloadEntity") fun downloads(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM DownloadEntity") suspend fun downloadsNow(): List<DownloadEntity>
    @Query("SELECT * FROM DownloadEntity WHERE videoId = :id") suspend fun download(id: String): DownloadEntity?
    @Query("DELETE FROM DownloadEntity WHERE videoId = :id") suspend fun deleteDownload(id: String)
    @Query("UPDATE DownloadEntity SET bytes = :b, total = :t WHERE videoId = :id") suspend fun updateProgress(id: String, b: Long, t: Long)
    @Query("UPDATE DownloadEntity SET status = 'FAILED' WHERE status = 'RUNNING'") suspend fun failStaleDownloads()
    @Query("SELECT v.* FROM CachedVideo v JOIN DownloadEntity d ON v.id = d.videoId ORDER BY d.createdAt DESC")
    fun downloadedVideos(): Flow<List<CachedVideo>>
}

@Database(
    entities = [ProfileEntity::class, CachedVideo::class, LibraryEntry::class, PlaylistEntity::class,
        PlaylistItem::class, NoteEntity::class, FollowEntity::class, HiddenCreator::class,
        HiddenTopic::class, SearchEntity::class, DownloadEntity::class],
    version = 1, exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
