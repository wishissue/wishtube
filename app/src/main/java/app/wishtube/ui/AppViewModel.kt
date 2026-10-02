package app.wishtube.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.wishtube.App
import app.wishtube.data.*
import app.wishtube.domain.SourceId
import app.wishtube.domain.Video
import app.wishtube.domain.creatorKey
import app.wishtube.recommend.RecWeights
import java.io.IOException

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as App).container
    private val c = container
    private val dao = c.dao
    val prefs = c.userPrefs
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // ---- profiles ----
    val profiles = dao.profiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val selectedId = MutableStateFlow(c.prefs.getLong("profile", -1L))
    val profile: StateFlow<ProfileEntity?> = combine(profiles, selectedId) { l, id ->
        l.firstOrNull { it.id == id } ?: l.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- recommendation weights (per profile) ----
    val weights = MutableStateFlow(RecWeights())
    fun setWeights(w: RecWeights) { weights.value = w; profile.value?.let { saveWeights(it.id, w) } }

    val nowPlaying = MutableStateFlow<Video?>(null)
    val dismissed = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch { if (dao.profileCount() == 0) dao.insertProfile(ProfileEntity(name = "Main")) }
        viewModelScope.launch { profile.filterNotNull().collect { weights.value = loadWeights(it.id) } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> perProfile(initial: T, f: (Long) -> Flow<T>): StateFlow<T> =
        profile.filterNotNull().flatMapLatest { f(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private fun lib(kind: String) = perProfile(emptyList<Video>()) { p -> dao.videosOf(p, kind).map { l -> l.map { it.toVideo() } } }
    val history = lib(Kind.HISTORY)
    val watchLater = lib(Kind.WATCH_LATER)
    val saved = lib(Kind.SAVED)
    val liked = lib(Kind.LIKED)
    val playlists = perProfile(emptyList<PlaylistEntity>()) { dao.playlists(it) }
    val follows = perProfile(emptyList<FollowEntity>()) { dao.follows(it) }
    val notes = perProfile(emptyList<NoteRow>()) { dao.notesWithVideos(it) }
    val recentSearches = perProfile(emptyList<SearchEntity>()) { dao.recentSearches(it) }
    val hiddenCreators = perProfile(emptySet<String>()) { p -> dao.hiddenCreators(p).map { l -> l.map { it.creatorKey }.toSet() } }

    // downloads are device-wide (files belong to the device, not a profile)
    val downloadMap: StateFlow<Map<String, DownloadEntity>> = dao.downloads().map { l -> l.associateBy { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val downloadedVideos: StateFlow<List<Video>> = dao.downloadedVideos().map { l -> l.map { it.toVideo() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flagsFor(videoId: String): Flow<Set<String>> = profile.filterNotNull()
        .flatMapLatest { p -> dao.entriesFor(p.id, videoId).map { l -> l.map { it.kind }.toSet() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun noteFor(videoId: String): Flow<String?> = profile.filterNotNull().flatMapLatest { dao.note(it.id, videoId) }

    fun playlistVideos(id: Long): Flow<List<Video>> = dao.playlistVideos(id).map { l -> l.map { it.toVideo() } }

    // ---- actions ----
    private inline fun withProfile(crossinline block: suspend (Long) -> Unit) {
        val p = profile.value ?: return
        viewModelScope.launch { block(p.id) }
    }

    fun toggle(video: Video, kind: String) = withProfile { p ->
        c.library.cache(listOf(video))
        val on = c.library.toggle(p, video.id, kind)
        val name = when (kind) { Kind.WATCH_LATER -> "Watch Later"; Kind.SAVED -> "Saved"; else -> "Liked locally" }
        messages.tryEmit(if (on) (if (kind == Kind.WATCH_LATER) "Added to $name" else name) else "Removed from $name")
    }

    fun notInterested(video: Video) = withProfile { p ->
        c.library.cache(listOf(video)); c.library.setFlag(p, video.id, Kind.NOT_INTERESTED)
        dismissed.value = dismissed.value + video.id
        messages.tryEmit("Got it. You'll see less like this.")
    }

    fun hideCreator(video: Video) = withProfile { p ->
        dao.putHiddenCreator(HiddenCreator(p, video.creatorKey)); messages.tryEmit("${video.creator} hidden")
    }

    fun hideTopic(topic: String) = withProfile { p -> dao.putHiddenTopic(HiddenTopic(p, topic)); messages.tryEmit("Topic “$topic” hidden") }

    fun isFollowing(creatorKey: String) = follows.value.any { it.creatorKey == creatorKey }

    fun toggleFollow(creatorKey: String, name: String, source: SourceId, creatorId: String) = withProfile { p ->
        if (follows.value.any { it.creatorKey == creatorKey }) { dao.removeFollow(p, creatorKey); messages.tryEmit("Unfollowed locally") }
        else { dao.putFollow(FollowEntity(p, creatorKey, name, source.name, creatorId)); messages.tryEmit("Following locally") }
    }

    fun toggleFollow(video: Video) = toggleFollow(video.creatorKey, video.creator, video.source, video.creatorId)

    fun saveNote(videoId: String, text: String) = withProfile { p ->
        if (text.isBlank()) dao.deleteNote(p, videoId) else dao.putNote(NoteEntity(p, videoId, text.trim()))
    }

    fun recordSearch(q: String) = withProfile { p -> if (q.isNotBlank()) dao.putSearch(SearchEntity(p, q.trim(), System.currentTimeMillis())) }
    fun clearSearches() = withProfile { p -> dao.deleteSearchesOf(p) }

    fun createPlaylist(name: String) = withProfile { p -> if (name.isNotBlank()) dao.insertPlaylist(PlaylistEntity(profileId = p, name = name.trim())) }
    fun deletePlaylist(id: Long) { viewModelScope.launch { dao.deletePlaylistItems(id); dao.deletePlaylist(id) } }
    fun addToPlaylist(playlistId: Long, video: Video) {
        viewModelScope.launch {
            c.library.cache(listOf(video)); dao.addToPlaylist(PlaylistItem(playlistId, video.id, System.currentTimeMillis()))
            messages.tryEmit("Added to playlist")
        }
    }

    fun saveProgress(video: Video, posMs: Long, durMs: Long) {
        if (posMs < 3_000) return
        withProfile { p -> c.library.saveProgress(p, video, posMs, durMs.coerceAtLeast(0)) }
    }

    suspend fun resumePosition(video: Video): Long {
        val p = profile.value?.id ?: return 0
        val e = dao.historyEntry(p, video.id) ?: return 0
        return if (e.progressMs > 5_000 && (e.durationMs == 0L || e.progressMs < e.durationMs - 10_000)) e.progressMs else 0
    }

    fun closePlayer() {
        nowPlaying.value?.let { saveProgress(it, c.player.currentPosition, c.player.duration) }
        c.player.stop(); c.player.clearMediaItems(); nowPlaying.value = null
    }

    // ---- downloads ----
    fun startDownload(video: Video) { c.downloads.start(video); messages.tryEmit("Downloading…") }
    fun cancelDownload(id: String) = c.downloads.cancel(id)
    fun deleteDownload(id: String) { viewModelScope.launch { c.downloads.delete(id); messages.tryEmit("Download deleted") } }
    fun deleteAllDownloads() { viewModelScope.launch { c.downloads.deleteAll(); messages.tryEmit("Downloads deleted") } }

    // ---- privacy / data ownership ----
    fun clearHistory() = withProfile { p -> dao.clearKind(p, Kind.HISTORY); messages.tryEmit("History cleared") }
    fun clearRecommendations() = withProfile { p ->
        c.library.clearRecommendationData(p); weights.value = RecWeights(); saveWeights(p, RecWeights())
        messages.tryEmit("Recommendation data cleared")
    }
    fun clearCache() { viewModelScope.launch { dao.clearCache(); messages.tryEmit("Cache cleared") } }

    fun exportProfile(resolver: ContentResolver, uri: Uri) {
        val p = profile.value ?: return
        viewModelScope.launch {
            try {
                val json = c.library.exportProfile(p, weights.value)
                withContext(Dispatchers.IO) {
                    (resolver.openOutputStream(uri) ?: throw IOException("Can't write to that location")).use { it.write(json.toByteArray()) }
                }
                messages.tryEmit("Profile exported")
            } catch (e: Exception) { messages.tryEmit("Export failed: ${e.message}") }
        }
    }

    fun importProfile(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw IOException("Can't read that file")
                }
                val (id, w) = c.library.importProfile(text)
                saveWeights(id, w); selectProfile(id)
                messages.tryEmit("Profile imported")
            } catch (e: Exception) { messages.tryEmit(e.message ?: "Import failed") }
        }
    }

    // ---- profiles ----
    fun selectProfile(id: Long) { c.prefs.edit().putLong("profile", id).apply(); selectedId.value = id }
    fun createProfile(name: String) { viewModelScope.launch { if (name.isNotBlank()) selectProfile(dao.insertProfile(ProfileEntity(name = name.trim()))) } }
    fun deleteProfile(p: ProfileEntity) {
        if (profiles.value.size <= 1) return
        viewModelScope.launch { c.library.deleteProfile(p.id); selectedId.value = -1L }
    }

    private fun loadWeights(p: Long): RecWeights {
        val d = RecWeights()
        fun g(k: String, def: Float) = c.prefs.getFloat("w${p}_$k", def)
        return RecWeights(g("topic", d.topic), g("creator", d.creator), g("new", d.newCreators), g("pop", d.popularity),
            g("rec", d.recency), g("small", d.smallCreators), g("sub", d.subscribed), g("rand", d.randomness),
            g("long", d.longVideos), g("short", d.shortVideos))
    }

    private fun saveWeights(p: Long, w: RecWeights) {
        c.prefs.edit().putFloat("w${p}_topic", w.topic).putFloat("w${p}_creator", w.creator).putFloat("w${p}_new", w.newCreators)
            .putFloat("w${p}_pop", w.popularity).putFloat("w${p}_rec", w.recency).putFloat("w${p}_small", w.smallCreators)
            .putFloat("w${p}_sub", w.subscribed).putFloat("w${p}_rand", w.randomness)
            .putFloat("w${p}_long", w.longVideos).putFloat("w${p}_short", w.shortVideos).apply()
    }
}
