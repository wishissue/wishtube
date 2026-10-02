package app.wishtube.sources

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.wishtube.domain.CreatorInfo
import app.wishtube.domain.SourceId
import app.wishtube.domain.Video
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/** Runs queries across sources in parallel. One failing source never breaks the others. */
class SourceManager(private val prefs: SharedPreferences) {
    val sources: List<VideoSource> = listOf(
        PeerTubeAdapter(prefs),
        OdyseeAdapter(),
        InternetArchiveAdapter(),
        MediaCccAdapter(),
        WikimediaAdapter()
    )
    /** In-memory registry of every video shown this session (used by the watch page). */
    val known = ConcurrentHashMap<String, Video>()

    private val _enabled = MutableStateFlow(
        SourceId.entries.filter { prefs.getBoolean("src_${it.name}", true) }.toSet())
    val enabled: StateFlow<Set<SourceId>> = _enabled

    private val _order = MutableStateFlow(loadOrder())
    val order: StateFlow<List<SourceId>> = _order

    private val _instance = MutableStateFlow(
        prefs.getString(PeerTubeAdapter.KEY_INSTANCE, null) ?: PeerTubeAdapter.DEFAULT_INSTANCE)
    val peerTubeInstance: StateFlow<String> = _instance

    private fun loadOrder(): List<SourceId> {
        val saved = prefs.getString("src_order", null)?.split(',')
            ?.mapNotNull { runCatching { SourceId.valueOf(it) }.getOrNull() }.orEmpty()
        return (saved + SourceId.entries).distinct()
    }

    fun setEnabled(id: SourceId, on: Boolean) {
        prefs.edit().putBoolean("src_${id.name}", on).apply()
        _enabled.value = if (on) _enabled.value + id else _enabled.value - id
    }

    /** Source order decides which source's results come first when feeds are interleaved. */
    fun move(id: SourceId, delta: Int) {
        val l = _order.value.toMutableList()
        val i = l.indexOf(id); if (i < 0) return
        val j = (i + delta).coerceIn(0, l.lastIndex)
        l.removeAt(i); l.add(j, id)
        _order.value = l
        prefs.edit().putString("src_order", l.joinToString(",")).apply()
    }

    fun validatePeerTubeInstanceUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        var s = trimmed
        if (!s.contains("://")) s = "https://$s"

        val uri = try {
            URI(s)
        } catch (e: Exception) {
            return "Invalid URL format"
        }

        if (uri.scheme != "https") {
            return "Only https:// URLs are allowed"
        }
        if (!uri.userInfo.isNullOrEmpty()) {
            return "URLs with user credentials are not allowed"
        }
        val host = uri.host ?: return "URL must include a valid host"
        val lowerHost = host.lowercase()

        if (lowerHost == "localhost" || lowerHost.endsWith(".local")) {
            return "Local and internal hosts are not allowed"
        }

        try {
            val inet = InetAddress.getByName(host)
            if (inet.isLoopbackAddress || inet.isSiteLocalAddress || inet.isLinkLocalAddress || inet.isAnyLocalAddress) {
                return "Local and private IP addresses are not allowed"
            }
            val bytes = inet.address
            if (bytes.size == 4) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 127 || b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168) || (b0 == 169 && b1 == 254)) {
                    return "Local and private IP addresses are not allowed"
                }
            } else if (bytes.size == 16) {
                val b0 = bytes[0].toInt() and 0xFF
                if (inet.isLoopbackAddress || (b0 and 0xFE) == 0xFC) {
                    return "Local and private IP addresses are not allowed"
                }
            }
        } catch (e: UnknownHostException) {
            if (lowerHost == "localhost" || lowerHost.endsWith(".local") || lowerHost.startsWith("127.") || lowerHost.startsWith("10.") || lowerHost.startsWith("192.168.")) {
                return "Local and private IP addresses are not allowed"
            }
        }

        return null
    }

    /** @return error message, or null when accepted */
    fun setPeerTubeInstance(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        val error = validatePeerTubeInstanceUrl(trimmed)
        if (error != null) return error

        var s = trimmed
        if (s.isEmpty()) s = PeerTubeAdapter.DEFAULT_INSTANCE
        if (!s.contains("://")) s = "https://$s"

        prefs.edit().putString(PeerTubeAdapter.KEY_INSTANCE, s).apply()
        _instance.value = s
        return null
    }

    fun source(id: SourceId): VideoSource = sources.first { it.id == id }

    data class Result(val videos: List<Video>, val errors: Map<SourceId, String>)

    suspend fun query(only: Set<SourceId>? = null, block: suspend (VideoSource) -> List<Video>): Result =
        coroutineScope {
            val active = _order.value.mapNotNull { id -> sources.firstOrNull { it.id == id } }
                .filter { it.id in _enabled.value && (only == null || it.id in only) }
            val outs = active.map { s -> async { s.id to runCatching { block(s) } } }.awaitAll()
            collect(outs)
        }

    /** Fetch videos for followed creators: (source, creatorId) pairs. */
    suspend fun creators(pairs: List<Pair<SourceId, String>>): Result = coroutineScope {
        val outs = pairs.filter { it.first in _enabled.value }.map { (sid, cid) ->
            async { sid to runCatching { source(sid).creatorVideos(cid, 0) } }
        }.awaitAll()
        collect(outs)
    }

    suspend fun creatorInfo(source: SourceId, creatorId: String): CreatorInfo? =
        runCatching { source(source).creatorInfo(creatorId) }.getOrNull()

    /** Deep links: ask each source whether it recognises the URL. */
    suspend fun resolveUrl(url: String): Video? {
        for (s in sources) {
            val v = runCatching { s.resolveUrl(url) }.getOrNull()
            if (v != null) { known[v.id] = v; return v }
        }
        return null
    }

    private fun collect(outs: List<Pair<SourceId, kotlin.Result<List<Video>>>>): Result {
        val errors = LinkedHashMap<SourceId, String>()
        val lists = ArrayList<List<Video>>()
        for ((id, r) in outs) {
            r.onSuccess { lists.add(it) }.onFailure {
                if (it is CancellationException) throw it
                errors[id] = it.message ?: it::class.java.simpleName
            }
        }
        val merged = interleave(lists)
        merged.forEach { known[it.id] = it }
        return Result(merged, errors)
    }

    private fun interleave(lists: List<List<Video>>): List<Video> {
        val out = ArrayList<Video>(); var i = 0
        while (lists.any { i < it.size }) { lists.forEach { if (i < it.size) out += it[i] }; i++ }
        return out
    }
}
