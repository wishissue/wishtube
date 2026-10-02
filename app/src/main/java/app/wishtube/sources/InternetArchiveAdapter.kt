package app.wishtube.sources

import kotlinx.serialization.json.*
import app.wishtube.domain.*
import java.io.IOException
import java.net.URI
import java.time.Instant

/**
 * Internet Archive via public Advanced Search and metadata APIs (https://archive.org/advancedsearch.php).
 * Consumes public moving image collections and direct MP4/OGV/WebM files.
 */
class InternetArchiveAdapter : VideoSource {
    override val id = SourceId.INTERNET_ARCHIVE
    override val capabilities = SourceCapabilities(search = true, downloads = true)
    private val pageSize = 20

    override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> {
        val s = when (sort) {
            SearchSort.RELEVANCE -> "downloads desc"
            SearchSort.NEWEST -> "date desc"
            SearchSort.OLDEST -> "date asc"
            SearchSort.POPULAR -> "downloads desc"
        }
        val q = "(mediatype:movies OR mediatype:audio) AND ($query)"
        val url = Http.url("https://archive.org/advancedsearch.php",
            "q" to q, "fl" to "identifier,title,description,creator,date,downloads,language",
            "sort[]" to s, "rows" to pageSize.toString(), "page" to (page + 1).toString(), "output" to "json")
        val root = Http.getJson(url)
        return parseDocs(root)
    }

    override suspend fun feed(page: Int, sort: FeedSort): List<Video> {
        val s = if (sort == FeedSort.NEWEST) "date desc" else "downloads desc"
        val q = "mediatype:movies AND (collection:feature_films OR collection:opensource_movies OR collection:silent_films)"
        val url = Http.url("https://archive.org/advancedsearch.php",
            "q" to q, "fl" to "identifier,title,description,creator,date,downloads,language",
            "sort[]" to s, "rows" to pageSize.toString(), "page" to (page + 1).toString(), "output" to "json")
        val root = Http.getJson(url)
        return parseDocs(root)
    }

    override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> {
        val q = "creator:\"$creatorId\" AND mediatype:movies"
        val url = Http.url("https://archive.org/advancedsearch.php",
            "q" to q, "fl" to "identifier,title,description,creator,date,downloads,language",
            "sort[]" to "date desc", "rows" to pageSize.toString(), "page" to (page + 1).toString(), "output" to "json")
        val root = Http.getJson(url)
        return parseDocs(root)
    }

    override suspend fun creatorInfo(creatorId: String): CreatorInfo? =
        CreatorInfo(name = creatorId, description = "Internet Archive collection / creator", avatar = null, followers = null, url = "https://archive.org/search.php?query=creator%3A%22$creatorId%22")

    override suspend fun resolveStream(video: Video): StreamInfo {
        val identifier = video.sourceVideoId
        val metaUrl = "https://archive.org/metadata/$identifier"
        val root = Http.getJson(metaUrl).obj() ?: throw IOException("Bad metadata response")
        val files = (root["files"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
        val videoFiles = files.filter { f ->
            val fmt = f["format"].str().orEmpty().lowercase()
            val name = f["name"].str().orEmpty().lowercase()
            fmt.contains("h.264") || fmt.contains("mpeg4") || fmt.contains("webm") || name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".ogv")
        }
        val best = videoFiles.maxByOrNull { f -> f["size"].str()?.toLongOrNull() ?: 0L } ?: videoFiles.firstOrNull()
        val fileDir = root["dir"].str().orEmpty()
        val server = root["server"].str().orEmpty()
        val fileName = best?.get("name").str() ?: throw IOException("No video file found")
        val url = "https://$server$fileDir/$fileName"
        return StreamInfo(url = url, isHls = false, downloadUrl = url)
    }

    override suspend fun resolveUrl(url: String): Video? {
        val u = runCatching { URI.create(url) }.getOrNull() ?: return null
        val host = u.host ?: return null
        if (!host.contains("archive.org")) return null
        val segs = u.path?.split('/')?.filter { it.isNotEmpty() } ?: return null
        if (segs.size < 2) return null
        val identifier = segs[1] // /details/{identifier}
        val metaUrl = "https://archive.org/metadata/$identifier"
        val root = runCatching { Http.getJson(metaUrl).obj() }.getOrNull() ?: return null
        val meta = root["metadata"].obj() ?: return null
        val title = meta["title"].str() ?: identifier
        val desc = meta["description"].str().orEmpty()
        val creator = meta["creator"].str() ?: "Internet Archive"
        val dateStr = meta["date"].str()
        val uploadDate = dateStr?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        return Video(
            id = "archive:$identifier", source = SourceId.INTERNET_ARCHIVE, sourceVideoId = identifier,
            title = title, description = desc, creator = creator, creatorId = creator,
            thumbnail = "https://archive.org/services/img/$identifier", durationSec = 0L,
            uploadDate = uploadDate, viewCount = null, tags = emptyList(), categories = listOf("Movies"),
            sourceUrl = "https://archive.org/details/$identifier"
        )
    }

    private fun parseDocs(root: JsonElement): List<Video> {
        val resp = root.obj()?.get("response").obj() ?: return emptyList()
        val docs = resp["docs"] as? JsonArray ?: return emptyList()
        return docs.mapNotNull { d ->
            val obj = d.obj() ?: return@mapNotNull null
            val id = obj["identifier"].str() ?: return@mapNotNull null
            val title = obj["title"].str() ?: return@mapNotNull null
            val creator = obj["creator"].str() ?: "Internet Archive"
            val desc = obj["description"].str().orEmpty()
            val dateStr = obj["date"].str()
            val uploadDate = dateStr?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            val downloads = obj["downloads"].long()
            Video(
                id = "archive:$id", source = SourceId.INTERNET_ARCHIVE, sourceVideoId = id,
                title = title, description = desc, creator = creator, creatorId = creator,
                thumbnail = "https://archive.org/services/img/$id", durationSec = 0L,
                uploadDate = uploadDate, viewCount = downloads, tags = emptyList(), categories = listOf("Moving Image"),
                sourceUrl = "https://archive.org/details/$id"
            )
        }
    }
}
