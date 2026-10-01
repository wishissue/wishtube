package org.openvideo.aggregator.sources

import android.net.Uri
import kotlinx.serialization.json.*
import org.openvideo.aggregator.domain.*
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/**
 * Odysee / LBRY via the public LBRY SDK JSON-RPC proxy.
 * Only free, non-encrypted streams are exposed. No DRM/paywall circumvention.
 * NOTE: endpoint and stream URL scheme are third-party and may change; verify before release.
 */
class OdyseeAdapter : VideoSource {
    override val id = SourceId.ODYSEE
    override val capabilities = SourceCapabilities(search = true, downloads = true)
    private val api = "https://api.na-backend.odysee.com/api/v1/proxy"
    private val pageSize = 20

    private fun arr(vararg s: String) = JsonArray(s.map { JsonPrimitive(it) })

    override suspend fun search(query: String, page: Int, sort: SearchSort): List<Video> = claimSearch {
        put("text", query); paging(page)
        when (sort) {
            SearchSort.NEWEST -> put("order_by", arr("release_time"))
            SearchSort.POPULAR -> put("order_by", arr("effective_amount"))
            else -> {}
        }
    }

    override suspend fun feed(page: Int, sort: FeedSort): List<Video> = claimSearch {
        paging(page)
        put("order_by", if (sort == FeedSort.NEWEST) arr("release_time") else arr("trending_group", "trending_mixed"))
    }

    override suspend fun creatorVideos(creatorId: String, page: Int): List<Video> = claimSearch {
        paging(page); put("channel_ids", arr(creatorId)); put("order_by", arr("release_time"))
    }

    override suspend fun creatorInfo(creatorId: String): CreatorInfo? {
        val res = rpc("claim_search", buildJsonObject {
            put("claim_ids", arr(creatorId)); put("claim_type", arr("channel"))
            put("page_size", 1); put("no_totals", true)
        })
        val item = (res["items"] as? JsonArray)?.firstOrNull().obj() ?: return null
        val v = item["value"].obj()
        return CreatorInfo(
            name = v?.get("title").str()?.takeIf { it.isNotBlank() } ?: item["name"].str()?.removePrefix("@").orEmpty(),
            description = v?.get("description").str().orEmpty(),
            avatar = v?.get("thumbnail").obj()?.get("url").str(),
            followers = null,                                   // not exposed by this API
            url = webUrl(item["canonical_url"].str()),
        )
    }

    /** sourceVideoId = "name|claimId|sdHash6" */
    override suspend fun resolveStream(video: Video): StreamInfo {
        val (name, claimId, sd) = video.sourceVideoId.split('|', limit = 3)
        val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val url = "https://player.odycdn.com/api/v3/streams/free/$encodedName/$claimId/$sd.mp4"
        return StreamInfo(url, isHls = false, downloadUrl = url)
    }

    override suspend fun resolveUrl(url: String): Video? {
        val lbry: String = if (url.startsWith("lbry://")) url else {
            val u = runCatching { URI.create(url) }.getOrNull() ?: return null
            val host = u.host?.removePrefix("www.") ?: return null
            if (host != "odysee.com") return null
            val segs = u.path?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
            when {
                segs.size >= 2 && segs[0].startsWith("@") ->
                    "lbry://" + segs[0].replace(':', '#') + "/" + segs[1].replace(':', '#')
                segs.size == 1 && !segs[0].startsWith("@") && !segs[0].startsWith("$") ->
                    "lbry://" + segs[0].replace(':', '#')
                else -> return null
            }
        }
        val res = rpc("resolve", buildJsonObject { put("urls", arr(lbry)) })
        val claim = res[lbry].obj() ?: res.values.firstOrNull().obj() ?: return null
        return parse(claim)
    }

    private fun JsonObjectBuilder.paging(page: Int) {
        put("page_size", pageSize); put("page", page + 1)
        put("claim_type", arr("stream")); put("stream_types", arr("video"))
        put("has_source", true); put("no_totals", true)
        put("not_tags", arr("mature", "porn", "nsfw", "xxx"))
    }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("method", method); put("id", 1); put("params", params)
        }
        return Http.postJson("$api?m=$method", body.toString()).obj()?.get("result").obj()
            ?: throw IOException("Unexpected response")
    }

    private suspend fun claimSearch(params: JsonObjectBuilder.() -> Unit): List<Video> {
        val res = rpc("claim_search", buildJsonObject(params))
        val items = res["items"] as? JsonArray ?: throw IOException("Unexpected response")
        return items.mapNotNull { parse(it) }
    }

    private fun webUrl(canonical: String?): String? =
        canonical?.removePrefix("lbry://")?.replace('#', ':')?.let { "https://odysee.com/$it" }

    private fun parse(e: JsonElement): Video? {
        val o = e.obj() ?: return null
        val v = o["value"].obj() ?: return null
        val fee = v["fee"].obj()?.get("amount").str()?.toDoubleOrNull() ?: 0.0
        if (fee > 0) return null                       // paid content: not supported
        val sd = v["source"].obj()?.get("sd_hash").str() ?: return null
        val name = o["name"].str() ?: return null
        val claimId = o["claim_id"].str() ?: return null
        val ch = o["signing_channel"].obj()
        val channelName = ch?.get("name").str() ?: "@anonymous"
        val creator = ch?.get("value").obj()?.get("title").str()?.takeIf { it.isNotBlank() }
            ?: channelName.removePrefix("@")
        val ts = (v["release_time"].str()?.toLongOrNull() ?: o["timestamp"].long() ?: 0L) * 1000
        return Video(
            id = "odysee:$claimId", source = SourceId.ODYSEE, sourceVideoId = "$name|$claimId|${sd.take(6)}",
            title = v["title"].str() ?: name, description = v["description"].str().orEmpty(),
            creator = creator, creatorId = ch?.get("claim_id").str() ?: "anonymous",
            thumbnail = v["thumbnail"].obj()?.get("url").str(),
            durationSec = v["video"].obj()?.get("duration").long() ?: 0L, uploadDate = ts,
            viewCount = null, tags = v["tags"].strList(), categories = emptyList(),
            sourceUrl = webUrl(o["canonical_url"].str()) ?: "https://odysee.com",
        )
    }
}
