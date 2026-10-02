package app.wishtube.sources

import kotlinx.serialization.json.*
import app.wishtube.domain.*
import java.io.IOException
import java.net.URI
import java.time.Instant

/**
 * media.ccc.de via public API (https://api.media.ccc.de/public/events).
 * Consumes public talks, lectures and conferences with direct MP4/HLS streams.
 */
class MediaCccAdapter : VideoSource {
    override val id = SourceId.MEDIA_CCC
    override val capabilities = SourceCapabilities(search = true, downloads = true)
    private val pageSize = 20

    private var cachedEvents: List<JsonObject>? = null
    private var cacheTime: Long = 0L

    private suspend fun fetchEvents(): List<JsonObject> {
        val now = System.currentTimeMillis()
        cachedEvents?.let { if (now - cacheTime < 300_000) return it }
        return runCatching {
            val root = Http.getJson("https://api.media.ccc.de/public/events")
            val obj = root.obj() ?: return@runCatching emptyList()
            val events = (obj["events"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
            cachedEvents = events
            cacheTime = now
            events
        }.getOrDefault(emptyList())
    }

    override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> {
        val all = fetchEvents()
        val q = query.lowercase()
        val filtered = all.filter { e ->
            val title = e["title"].str().orEmpty().lowercase()
            val desc = e["description"].str().orEmpty().lowercase()
            val subtitle = e["subtitle"].str().orEmpty().lowercase()
            title.contains(q) || desc.contains(q) || subtitle.contains(q)
        }
        return filtered.drop(page * pageSize).take(pageSize).mapNotNull { parseEvent(it) }
    }

    override suspend fun feed(page: Int, sort: FeedSort): List<Video> {
        val all = fetchEvents()
        val sorted = if (sort == FeedSort.NEWEST) {
            all.sortedByDescending { e -> e["date"].str() ?: "" }
        } else {
            all.sortedByDescending { e -> e["view_count"].long() ?: 0L }
        }
        return sorted.drop(page * pageSize).take(pageSize).mapNotNull { parseEvent(it) }
    }

    override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> {
        val all = fetchEvents()
        val filtered = all.filter { e ->
            val persons = (e["persons"] as? JsonArray)?.mapNotNull { it.str() }.orEmpty()
            persons.any { it.equals(creatorId, ignoreCase = true) }
        }
        return filtered.drop(page * pageSize).take(pageSize).mapNotNull { parseEvent(it) }
    }

    override suspend fun creatorInfo(creatorId: String): CreatorInfo? =
        CreatorInfo(name = creatorId, description = "Speaker on media.ccc.de", avatar = null, followers = null, url = "https://media.ccc.de")

    override suspend fun resolveStream(video: Video): StreamInfo {
        val guid = video.sourceVideoId
        val root = Http.getJson("https://api.media.ccc.de/public/events/$guid").obj() ?: throw IOException("Event not found")
        val recordings = root["recordings"] as? JsonArray ?: throw IOException("No recordings")
        val recs = recordings.mapNotNull { it.obj() }
        val mp4s = recs.filter { r -> r["mime_type"].str().orEmpty().contains("mp4") || r["recording_url"].str().orEmpty().endsWith(".mp4") }
        val best = mp4s.maxByOrNull { r -> r["width"].long() ?: 0L } ?: mp4s.firstOrNull() ?: recs.firstOrNull()
        val url = best?.get("recording_url").str() ?: throw IOException("No stream URL")
        return StreamInfo(url = url, isHls = false, downloadUrl = url)
    }

    override suspend fun resolveUrl(url: String): Video? {
        val u = runCatching { URI.create(url) }.getOrNull() ?: return null
        if (!u.host.orEmpty().contains("media.ccc.de")) return null
        val segs = u.path?.split('/')?.filter { it.isNotEmpty() } ?: return null
        if (segs.size < 2 || segs[0] != "v") return null
        val guid = segs[1]
        val root = runCatching { Http.getJson("https://api.media.ccc.de/public/events/$guid").obj() }.getOrNull() ?: return null
        return parseEvent(root)
    }

    private fun parseEvent(e: JsonObject): Video? {
        val guid = e["guid"].str() ?: e["id"].str() ?: return null
        val title = e["title"].str() ?: return null
        val desc = e["description"].str() ?: e["subtitle"].str().orEmpty()
        val persons = (e["persons"] as? JsonArray)?.mapNotNull { it.str() }.orEmpty()
        val creator = if (persons.isNotEmpty()) persons.joinToString(", ") else "media.ccc.de"
        val thumb = e["poster_url"].str() ?: e["thumb_url"].str()
        val dateStr = e["date"].str() ?: e["release_date"].str()
        val uploadDate = dateStr?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val duration = e["duration"].long() ?: 0L
        val views = e["view_count"].long()
        val tags = (e["tags"] as? JsonArray)?.mapNotNull { it.str() }.orEmpty()
        val url = e["url"].str() ?: "https://media.ccc.de/v/$guid"
        return Video(
            id = "mediaccc:$guid", source = SourceId.MEDIA_CCC, sourceVideoId = guid,
            title = title, description = desc, creator = creator, creatorId = creator,
            thumbnail = thumb, durationSec = duration, uploadDate = uploadDate,
            viewCount = views, tags = tags, categories = listOf("Conference"), sourceUrl = url
        )
    }
}
