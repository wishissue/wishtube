package org.openvideo.aggregator.sources

import kotlinx.serialization.json.*
import org.openvideo.aggregator.domain.*
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/**
 * Wikimedia Commons via MediaWiki Action API (https://commons.wikimedia.org/w/api.php).
 * Consumes CC-licensed video files.
 */
class WikimediaAdapter : VideoSource {
    override val id = SourceId.WIKIMEDIA
    override val capabilities = SourceCapabilities(search = true, downloads = true)
    private val api = "https://commons.wikimedia.org/w/api.php"

    override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> {
        val url = Http.url(api,
            "action" to "query", "list" to "search", "srsearch" to "$query filetype:video",
            "srlimit" to "20", "sroffset" to (page * 20).toString(), "format" to "json", "origin" to "*")
        val root = Http.getJson(url).obj() ?: return emptyList()
        val queryObj = root["query"].obj() ?: return emptyList()
        val search = queryObj["search"] as? JsonArray ?: return emptyList()
        return search.mapNotNull { it.obj()?.get("title").str()?.let { title -> fetchVideoInfo(title) } }
    }

    override suspend fun feed(page: Int, sort: FeedSort): List<Video> {
        val url = Http.url(api,
            "action" to "query", "list" to "search", "srsearch" to "filetype:video",
            "srlimit" to "20", "sroffset" to (page * 20).toString(), "format" to "json", "origin" to "*")
        val root = Http.getJson(url).obj() ?: return emptyList()
        val queryObj = root["query"].obj() ?: return emptyList()
        val search = queryObj["search"] as? JsonArray ?: return emptyList()
        return search.mapNotNull { it.obj()?.get("title").str()?.let { title -> fetchVideoInfo(title) } }
    }

    override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> {
        return search(creatorId, page, SearchSort.RELEVANCE)
    }

    override suspend fun creatorInfo(creatorId: String): CreatorInfo? =
        CreatorInfo(name = creatorId, description = "Wikimedia Commons contributor", avatar = null, followers = null, url = "https://commons.wikimedia.org")

    override suspend fun resolveStream(video: Video): StreamInfo {
        val title = video.sourceVideoId
        val v = fetchVideoInfo(title) ?: throw IOException("Video not found")
        return StreamInfo(url = v.sourceUrl, isHls = false, downloadUrl = v.sourceUrl)
    }

    override suspend fun resolveUrl(url: String): Video? {
        val u = runCatching { URI.create(url) }.getOrNull() ?: return null
        if (!u.host.orEmpty().contains("wikimedia.org")) return null
        val title = u.path?.substringAfterLast('/')?.let { URLDecoder.decode(it, "UTF-8") } ?: return null
        return fetchVideoInfo(if (title.startsWith("File:")) title else "File:$title")
    }

    private suspend fun fetchVideoInfo(title: String): Video? {
        val url = Http.url(api,
            "action" to "query", "prop" to "imageinfo", "iiprop" to "url|size|mime|extmetadata",
            "titles" to title, "format" to "json", "origin" to "*")
        val root = Http.getJson(url).obj() ?: return null
        val query = root["query"].obj() ?: return null
        val pages = query["pages"].obj() ?: return null
        val pageObj = pages.values.firstOrNull().obj() ?: return null
        val imageinfo = (pageObj["imageinfo"] as? JsonArray)?.firstOrNull().obj() ?: return null
        val fileUrl = imageinfo["url"].str() ?: return null
        if (!fileUrl.endsWith(".webm", ignoreCase = true) && !fileUrl.endsWith(".ogv", ignoreCase = true) && !fileUrl.endsWith(".mp4", ignoreCase = true)) {
            return null
        }
        val ext = imageinfo["extmetadata"].obj()
        val caption = ext?.get("ImageDescription")?.obj()?.get("value").str() ?: title
        val artist = ext?.get("Artist")?.obj()?.get("value").str()?.replace(Regex("<.*?>"), "") ?: "Wikimedia User"
        return Video(
            id = "wikimedia:${title.hashCode()}", source = SourceId.WIKIMEDIA, sourceVideoId = title,
            title = title.removePrefix("File:").substringBeforeLast('.'), description = caption,
            creator = artist, creatorId = artist, thumbnail = fileUrl, durationSec = 0L,
            uploadDate = 0L, viewCount = null, tags = emptyList(), categories = listOf("Wikimedia"),
            sourceUrl = fileUrl
        )
    }
}
