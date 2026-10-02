package app.wishtube.sources

import app.wishtube.domain.*

/** Every platform implements this. The UI never talks to platform APIs directly. */
interface VideoSource {
    val id: SourceId
    val capabilities: SourceCapabilities
    suspend fun search(query: String, page: Int, sort: SearchSort): List<Video>
    suspend fun feed(page: Int, sort: FeedSort): List<Video>
    suspend fun creatorVideos(creatorId: String, page: Int): List<Video>
    suspend fun creatorInfo(creatorId: String): CreatorInfo?
    suspend fun resolveStream(video: Video): StreamInfo
    /** Deep links: turn a source URL into a Video, or null if this source doesn't recognise the URL. */
    suspend fun resolveUrl(url: String): Video?
}
