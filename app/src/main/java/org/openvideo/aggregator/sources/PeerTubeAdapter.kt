package org.openvideo.aggregator.sources

import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.openvideo.aggregator.domain.*
import java.io.IOException
import java.net.URI
import java.time.Instant

/**
 * PeerTube via the public REST API (https://docs.joinpeertube.org/api-rest-reference.html).
 * Search uses SepiaSearch (cross-instance index); feed uses the instance chosen in Settings.
 * No PeerTube code is copied; only public HTTP APIs are called.
 */
class PeerTubeAdapter(private val prefs: SharedPreferences) : VideoSource {
    override val id = SourceId.PEERTUBE
    override val capabilities = SourceCapabilities(search = true, captions = true, chapters = true, downloads = true)
    private val searchBase = "https://sepiasearch.org"
    private val pageSize = 20

    private fun feedInstance(): String =
        prefs.getString(KEY_INSTANCE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_INSTANCE

    override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> {
        val s = when (sort) {
            SearchSort.RELEVANCE -> "-match"; SearchSort.NEWEST -> "-publishedAt"
            SearchSort.OLDEST -> "publishedAt"; SearchSort.POPULAR -> "-views"
        }
        val root = Http.getJson(Http.url("$searchBase/api/v1/search/videos",
            "search" to query, "start" to (page * pageSize).toString(),
            "count" to pageSize.toString(), "sort" to s, "nsfw" to "false"))
        return list(root, searchBase)
    }

    override suspend fun feed(page: Int, sort: FeedSort): List<Video> {
        val base = feedInstance()
        val s = if (sort == FeedSort.NEWEST) "-publishedAt" else "-trending"
        val root = Http.getJson(Http.url("$base/api/v1/videos",
            "start" to (page * pageSize).toString(), "count" to pageSize.toString(),
            "sort" to s, "nsfw" to "false"))
        return list(root, base)
    }

    /** creatorId = "channelName@host" */
    override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> {
        val base = "https://${creatorId.substringAfter('@')}"
        val root = Http.getJson(Http.url("$base/api/v1/video-channels/$creatorId/videos",
            "start" to (page * pageSize).toString(), "count" to pageSize.toString(), "sort" to "-publishedAt"))
        return list(root, base)
    }

    override suspend fun creatorInfo(creatorId: String): CreatorInfo? {
        val host = creatorId.substringAfter('@')
        val o = Http.getJson("https://$host/api/v1/video-channels/$creatorId").obj() ?: return null
        val avatar = (o["avatars"] as? JsonArray)?.lastOrNull().obj()?.get("path").str()
            ?: o["avatar"].obj()?.get("path").str()
        return CreatorInfo(
            name = o["displayName"].str() ?: creatorId.substringBefore('@'),
            description = o["description"].str().orEmpty(),
            avatar = avatar?.let { "https://$host$it" },
            followers = o["followersCount"].long(),
            url = o["url"].str() ?: "https://$host/c/${creatorId.substringBefore('@')}",
        )
    }

    /** sourceVideoId = "host|uuid". Prefers HLS (adaptive); also fetches captions, chapters and full description. */
    override suspend fun resolveStream(video: Video): StreamInfo = coroutineScope {
        val (host, uuid) = video.sourceVideoId.split('|', limit = 2)
        val api = "https://$host/api/v1/videos/$uuid"
        val capsD = async { runCatching { captions(host, api) }.getOrDefault(emptyList()) }
        val chapD = async { runCatching { chapters(api) }.getOrDefault(emptyList()) }
        val descD = async { runCatching { Http.getJson("$api/description").obj()?.get("description").str() }.getOrNull() }

        val o = Http.getJson(api).obj() ?: throw IOException("Bad response")
        fun res(f: JsonObject) = f["resolution"].obj()?.get("id").long() ?: 0L
        val webFiles = ((o["files"] as? JsonArray) ?: (o["webVideoFiles"] as? JsonArray))
            ?.mapNotNull { it.obj() }.orEmpty().filter { it["fileUrl"].str() != null }
        
        val sortedFiles = webFiles.sortedByDescending { res(it) }
        val mp4Urls = sortedFiles.mapNotNull { f ->
            val u = f["fileUrl"].str() ?: return@mapNotNull null
            if (u.startsWith("/")) "https://$host$u" else u
        }
        val hlsRaw = (o["streamingPlaylists"] as? JsonArray)?.firstOrNull().obj()?.get("playlistUrl").str()
        val hls = hlsRaw?.let { if (it.startsWith("/")) "https://$host$it" else it }

        val downloadRaw = sortedFiles.firstOrNull()?.get("fileDownloadUrl").str() ?: mp4Urls.firstOrNull()
        val download = downloadRaw?.let { if (it.startsWith("/")) "https://$host$it" else it }

        val candidates = mutableListOf<String>()
        if (hls != null) candidates.add(hls)
        candidates.addAll(mp4Urls)
        if (download != null && download !in candidates) candidates.add(download)

        if (candidates.isEmpty()) throw IOException("No playable stream (video may be private, live or restricted)")
        StreamInfo(urls = candidates, isHls = hls != null, downloadUrl = download, captions = capsD.await(), chapters = chapD.await(), description = descD.await())
    }

    override suspend fun resolveUrl(url: String): Video? {
        val u = runCatching { URI.create(url) }.getOrNull() ?: return null
        val host = u.host ?: return null
        val segs = u.path?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
        val id = when {
            segs.size >= 2 && segs[0] == "w" -> segs[1]
            segs.size >= 3 && segs[0] == "videos" && segs[1] == "watch" -> segs[2]
            else -> return null
        }
        val o = Http.getJson("https://$host/api/v1/videos/$id")
        return parse(o, "https://$host")
    }

    private suspend fun captions(host: String, api: String): List<Caption> =
        ((Http.getJson("$api/captions").obj()?.get("data")) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val path = o["captionPath"].str() ?: return@mapNotNull null
            val lang = o["language"].obj()
            Caption("https://$host$path", lang?.get("id").str() ?: "und", lang?.get("label").str() ?: "Captions")
        }

    private suspend fun chapters(api: String): List<Chapter> =
        ((Http.getJson("$api/chapters").obj()?.get("chapters")) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Chapter((o["timecode"].long() ?: return@mapNotNull null).toInt(), o["title"].str() ?: return@mapNotNull null)
        }

    private fun list(root: JsonElement, pathBase: String): List<Video> =
        (root.obj()?.get("data") as? JsonArray)?.mapNotNull { parse(it, pathBase) } ?: emptyList()

    private fun parse(e: JsonElement, pathBase: String): Video? {
        val o = e.obj() ?: return null
        val uuid = o["uuid"].str() ?: return null
        val channel = o["channel"].obj()
        val account = o["account"].obj()
        val host = channel?.get("host").str() ?: account?.get("host").str() ?: return null
        val chName = channel?.get("name").str() ?: account?.get("name").str() ?: return null
        val creator = channel?.get("displayName").str() ?: account?.get("displayName").str() ?: chName
        val thumb = o["thumbnailUrl"].str() ?: o["thumbnailPath"].str()?.let { pathBase + it }
        val published = o["publishedAt"].str()
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val title = o["name"].str() ?: return null
        return Video(
            id = "peertube:$host|$uuid", source = SourceId.PEERTUBE, sourceVideoId = "$host|$uuid",
            title = title, description = o["description"].str().orEmpty(),
            creator = creator, creatorId = "$chName@$host", thumbnail = thumb,
            durationSec = o["duration"].long() ?: 0L, uploadDate = published,
            viewCount = o["views"].long(), tags = o["tags"].strList(),
            categories = listOfNotNull(o["category"].obj()?.get("label").str()),
            sourceUrl = o["url"].str() ?: "https://$host/w/$uuid",
        )
    }

    companion object {
        const val KEY_INSTANCE = "pt_instance"
        const val DEFAULT_INSTANCE = "https://framatube.org"
    }
}
