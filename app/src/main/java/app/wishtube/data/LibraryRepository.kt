package app.wishtube.data

import androidx.room.withTransaction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import app.wishtube.domain.Video
import app.wishtube.domain.creatorKey
import app.wishtube.recommend.ProfileSignals
import app.wishtube.recommend.RecWeights
import app.wishtube.recommend.Topics
import java.io.IOException

class LibraryRepository(private val db: AppDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val word = Regex("[\\p{L}\\p{N}]{3,}")

    suspend fun cache(videos: List<Video>) {
        val now = System.currentTimeMillis()
        dao.upsertVideos(videos.map { it.toCached(now) })
    }

    /** @return true when the flag is now ON */
    suspend fun toggle(p: Long, videoId: String, kind: String): Boolean =
        if (dao.hasEntry(p, videoId, kind) > 0) { dao.removeEntry(p, videoId, kind); false }
        else { dao.putEntry(LibraryEntry(p, videoId, kind, System.currentTimeMillis())); true }

    suspend fun setFlag(p: Long, videoId: String, kind: String) =
        dao.putEntry(LibraryEntry(p, videoId, kind, System.currentTimeMillis()))

    suspend fun saveProgress(p: Long, video: Video, posMs: Long, durMs: Long) {
        cache(listOf(video))
        val dur = if (durMs > 0) durMs else video.durationSec * 1000
        dao.putEntry(LibraryEntry(p, video.id, Kind.HISTORY, System.currentTimeMillis(), posMs, dur))
    }

    suspend fun deleteProfile(id: Long) = db.withTransaction {
        dao.deletePlaylistItemsOf(id); dao.deletePlaylistsOf(id); dao.deleteEntriesOf(id)
        dao.deleteNotesOf(id); dao.deleteFollowsOf(id); dao.deleteHiddenCreatorsOf(id)
        dao.deleteHiddenTopicsOf(id); dao.deleteSearchesOf(id); dao.deleteProfile(id)
    }

    /** Wipes the recommendation inputs (likes, Not Interested, hidden items, searches) but keeps history. */
    suspend fun clearRecommendationData(p: Long) = db.withTransaction {
        dao.clearKind(p, Kind.LIKED); dao.clearKind(p, Kind.NOT_INTERESTED)
        dao.deleteHiddenCreatorsOf(p); dao.deleteHiddenTopicsOf(p); dao.deleteSearchesOf(p)
    }

    suspend fun exportProfile(p: ProfileEntity, weights: RecWeights): String {
        val entries = dao.entriesAll(p.id)
        val playlists = dao.playlistsNow(p.id).map { pl -> ExpPlaylist(pl.name, dao.playlistItemsNow(pl.id).map { it.videoId }) }
        val notes = dao.notesNow(p.id)
        val ids = (entries.map { it.videoId } + playlists.flatMap { it.videoIds } + notes.map { it.videoId }).distinct()
        val videos = ids.chunked(400).flatMap { dao.videosByIds(it) }
        val export = ProfileExport(
            name = p.name, videos = videos, entries = entries, playlists = playlists, notes = notes,
            follows = dao.followsNow(p.id), hiddenCreators = dao.hiddenCreatorsNow(p.id),
            hiddenTopics = dao.hiddenTopicsNow(p.id), searches = dao.searchesNow(p.id), weights = weights)
        return json.encodeToString(ProfileExport.serializer(), export)
    }

    /** Creates a NEW profile from an export. Never overwrites existing profiles. @return new profile id + weights */
    suspend fun importProfile(text: String): Pair<Long, RecWeights> {
        val e = try { json.decodeFromString(ProfileExport.serializer(), text) }
        catch (ex: SerializationException) { throw IOException("That file isn't a valid WishTube profile export.") }
        catch (ex: IllegalArgumentException) { throw IOException("That file isn't a valid WishTube profile export.") }
        if (e.version > 1) throw IOException("This export was made by a newer version of the app.")
        val id = db.withTransaction {
            val newId = dao.insertProfile(ProfileEntity(name = e.name + " (imported)"))
            dao.upsertVideos(e.videos)
            e.entries.forEach { dao.putEntry(it.copy(profileId = newId)) }
            e.playlists.forEach { pl ->
                val plId = dao.insertPlaylist(PlaylistEntity(profileId = newId, name = pl.name))
                pl.videoIds.forEach { dao.addToPlaylist(PlaylistItem(plId, it, System.currentTimeMillis())) }
            }
            e.notes.forEach { dao.putNote(it.copy(profileId = newId)) }
            e.follows.forEach { dao.putFollow(it.copy(profileId = newId)) }
            e.hiddenCreators.forEach { dao.putHiddenCreator(it.copy(profileId = newId)) }
            e.hiddenTopics.forEach { dao.putHiddenTopic(it.copy(profileId = newId)) }
            e.searches.forEach { dao.putSearch(it.copy(profileId = newId)) }
            newId
        }
        return id to e.weights
    }

    /** Derive compact recommendation signals from this profile's own library. */
    suspend fun signals(p: Long, interests: Set<String> = emptySet()): ProfileSignals {
        val entries = dao.entriesNow(p)
        val vids = dao.videosByIds(entries.map { it.videoId }.distinct().take(400)).associateBy { it.id }
        val topic = HashMap<String, Float>(); val creator = HashMap<String, Float>()
        val seen = HashSet<String>(); val ni = HashSet<String>()
        for (e in entries) {
            val v = vids[e.videoId]?.toVideo() ?: continue
            val w = when (e.kind) {
                Kind.HISTORY -> { seen += v.id
                    if (e.durationMs > 0) (e.progressMs.toFloat() / e.durationMs).coerceIn(0.2f, 1f) else 0.5f }
                Kind.LIKED -> 2f
                Kind.SAVED, Kind.WATCH_LATER -> 1f
                Kind.NOT_INTERESTED -> { ni += v.id; -2f }
                else -> 0f
            }
            Topics.of(v).forEach { topic[it] = (topic[it] ?: 0f) + w }
            creator[v.creatorKey] = (creator[v.creatorKey] ?: 0f) + w
        }
        // Search behaviour: words from recent searches count as interests.
        dao.searchesNow(p).forEach { s ->
            word.findAll(s.term.lowercase()).forEach { m -> topic[m.value] = (topic[m.value] ?: 0f) + 0.8f }
        }
        return ProfileSignals(
            topicAffinity = topic, creatorAffinity = creator,
            followed = dao.followsNow(p).map { it.creatorKey }.toSet(),
            hiddenCreators = dao.hiddenCreatorsNow(p).map { it.creatorKey }.toSet(),
            hiddenTopics = dao.hiddenTopicsNow(p).map { it.topic }.toSet(),
            seen = seen, notInterested = ni,
            selectedInterests = interests
        )
    }
}
