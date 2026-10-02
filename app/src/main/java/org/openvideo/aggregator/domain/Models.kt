package org.openvideo.aggregator.domain

enum class SourceId(val label: String) {
    PEERTUBE("PeerTube"),
    ODYSEE("Odysee"),
    INTERNET_ARCHIVE("Internet Archive"),
    MEDIA_CCC("media.ccc.de"),
    WIKIMEDIA("Wikimedia Commons")
}

/** What a source can really do. The UI only shows features a source supports. */
data class SourceCapabilities(
    val streaming: Boolean = true,
    val search: Boolean = true,
    val subscriptions: Boolean = false,
    val comments: Boolean = false,
    val likes: Boolean = false,
    val downloads: Boolean = false,
    val captions: Boolean = false,
    val chapters: Boolean = false,
    val audioTracks: Boolean = false,
    val liveStreams: Boolean = false,
    val playlists: Boolean = false,
    val authentication: Boolean = false,
)

/** Unified video model. Every adapter converts its platform data into this. */
data class Video(
    val id: String,               // "<source>:<sourceVideoId>"
    val source: SourceId,
    val sourceVideoId: String,
    val title: String,
    val description: String,
    val creator: String,
    val creatorId: String,
    val thumbnail: String?,
    val durationSec: Long,
    val uploadDate: Long,         // epoch millis, 0 if unknown
    val viewCount: Long?,         // null if the source does not provide it
    val tags: List<String>,
    val categories: List<String>,
    val sourceUrl: String,
)

val Video.creatorKey: String get() = "${source.name}:$creatorId"

data class Caption(val url: String, val lang: String, val label: String)

data class Chapter(val startSec: Int, val title: String)

/** Resolved playback info. Extra fields are optional and only filled when the source provides them. */
data class StreamInfo(
    val urls: List<String>,
    val isHls: Boolean,
    val downloadUrl: String? = null,          // direct file URL if the source allows downloading
    val captions: List<Caption> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val description: String? = null,          // full description when the list endpoint truncates it
) {
    val url: String get() = urls.firstOrNull() ?: ""
    constructor(
        url: String,
        isHls: Boolean,
        downloadUrl: String? = null,
        captions: List<Caption> = emptyList(),
        chapters: List<Chapter> = emptyList(),
        description: String? = null,
    ) : this(listOf(url), isHls, downloadUrl, captions, chapters, description)
}

data class CreatorInfo(
    val name: String, val description: String, val avatar: String?, val followers: Long?, val url: String?,
)

enum class FeedSort { TRENDING, NEWEST }
enum class SearchSort(val label: String) {
    RELEVANCE("Relevance"), NEWEST("Newest"), OLDEST("Oldest"), POPULAR("Popular")
}

enum class FeedMode(val label: String) {
    FOR_YOU("For You"), FOLLOWING("Following"), NEW("New"), DISCOVER("Discover"),
    SMALL("Small creators"), RANDOM("Random"), CHRONOLOGICAL("Chronological")
}

/** Chapters are parsed from timestamp lines in the description (works for any source). */
fun parseChapters(desc: String): List<Chapter> {
    val re = Regex("""^\s*(?:[-•*]\s*)?\(?((?:\d{1,2}:)?\d{1,2}:\d{2})\)?\s*[-–—:]?\s*(.+?)\s*$""", RegexOption.MULTILINE)
    val list = re.findAll(desc).map { m ->
        val secs = m.groupValues[1].split(':').map { it.toInt() }.fold(0) { a, b -> a * 60 + b }
        Chapter(secs, m.groupValues[2])
    }.toList()
    val ok = list.size >= 3 && list.first().startSec <= 5 &&
        list.zipWithNext().all { (a, b) -> b.startSec > a.startSec }
    return if (ok) list else emptyList()
}
