package org.openvideo.aggregator.recommend

import org.openvideo.aggregator.domain.*
import kotlinx.serialization.Serializable
import kotlin.math.*
import kotlin.random.Random

/** User-adjustable weights, each 0..1. Stored per profile. */
@Serializable
data class RecWeights(
    val topic: Float = 0.8f, val creator: Float = 0.6f, val newCreators: Float = 0.7f,
    val popularity: Float = 0.3f, val recency: Float = 0.7f, val smallCreators: Float = 0.5f,
    val subscribed: Float = 0.8f, val randomness: Float = 0.3f,
    val longVideos: Float = 0.5f, val shortVideos: Float = 0.5f,
)

/** Compact local signals derived from the profile's own data. Never leaves the device. */
data class ProfileSignals(
    val topicAffinity: Map<String, Float> = emptyMap(),
    val creatorAffinity: Map<String, Float> = emptyMap(),
    val followed: Set<String> = emptySet(),
    val hiddenCreators: Set<String> = emptySet(),
    val hiddenTopics: Set<String> = emptySet(),
    val seen: Set<String> = emptySet(),
    val notInterested: Set<String> = emptySet(),
    val selectedInterests: Set<String> = emptySet(),
)

data class Ranked(val video: Video, val score: Float, val reasons: List<String>)

object Topics {
    /** A stable, human-friendly topic for "Hide topic": prefers explicit tags, then categories. */
    fun primary(v: Video): String? = (v.tags + v.categories).map { it.trim().lowercase() }.firstOrNull { it.length in 2..30 }

    private val stop = setOf("about", "which", "their", "there", "would", "these", "those", "video", "videos",
        "episode", "again", "being", "first", "never", "really", "where", "while", "should", "could")
    private val word = Regex("[\\p{L}\\p{N}]{5,}")

    fun of(v: Video): Set<String> {
        val out = HashSet<String>()
        (v.tags + v.categories).forEach { val t = it.trim().lowercase(); if (t.length in 2..30) out += t }
        word.findAll(v.title.lowercase()).forEach { if (it.value !in stop) out += it.value }
        return out
    }
}

object RecommendationEngine {
    fun rank(
        candidates: List<Video>, sig: ProfileSignals, w: RecWeights, mode: FeedMode,
        now: Long = System.currentTimeMillis(), rng: Random = Random.Default,
    ): List<Ranked> {
        val pool = candidates.distinctBy { it.id }.filter { v ->
            v.title.isNotBlank() && v.sourceVideoId.isNotBlank() &&
                v.creatorKey !in sig.hiddenCreators && v.id !in sig.notInterested &&
                Topics.of(v).none { it in sig.hiddenTopics }
        }
        fun plain(v: Video, why: String) = Ranked(v, 0f, listOf(why))
        return when (mode) {
            FeedMode.NEW -> pool.sortedByDescending { it.uploadDate }.map { plain(it, "Recently published") }
            FeedMode.CHRONOLOGICAL -> pool.sortedByDescending { it.uploadDate }.map { plain(it, "Newest first") }
            FeedMode.RANDOM -> pool.shuffled(rng).map { plain(it, "Random pick") }
            FeedMode.FOLLOWING -> pool.filter { it.creatorKey in sig.followed }
                .sortedByDescending { it.uploadDate }.map { plain(it, "From a creator you follow") }
            else -> pool.map { score(it, sig, w, mode, now, rng) }.sortedByDescending { it.score }
        }
    }

    private fun score(v: Video, sig: ProfileSignals, w: RecWeights, mode: FeedMode, now: Long, rng: Random): Ranked {
        val topics = Topics.of(v)
        val topic = if (topics.isEmpty()) 0f else tanh(
            topics.sumOf { (sig.topicAffinity[it] ?: 0f).toDouble() } / (2.0 + sqrt(topics.size.toDouble()))).toFloat()
        val interestMatch = if (sig.selectedInterests.isEmpty()) 0f else {
            val matches = topics.count { t -> sig.selectedInterests.any { i -> t.contains(i.lowercase()) || i.lowercase().contains(t) } }
            (matches.toFloat() / sig.selectedInterests.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        val creatorFam = tanh((sig.creatorAffinity[v.creatorKey] ?: 0f).toDouble()).toFloat()
        val subscribed = if (v.creatorKey in sig.followed) 1f else 0f
        val isNew = v.creatorKey !in sig.creatorAffinity && subscribed == 0f
        val ageDays = ((now - v.uploadDate).coerceAtLeast(0)) / 86_400_000.0
        val recency = if (v.uploadDate <= 0) 0.3f else exp(-ageDays / 14.0).toFloat()
        val pop = v.viewCount?.let { (log10(it + 1.0) / 6.0).coerceIn(0.0, 1.0).toFloat() } ?: 0.4f
        val small = 1f - pop
        val mins = v.durationSec / 60f
        val shortF = if (mins < 5) 1f else 0f
        val longF = if (mins >= 20) 1f else 0f
        val length = shortF * w.shortVideos + longF * w.longVideos +
            (1 - shortF) * (1 - longF) * (w.shortVideos + w.longVideos) / 2
        val rand = rng.nextFloat()
        val seenPenalty = if (v.id in sig.seen) 0.8f else 0f

        val s = when (mode) {
            FeedMode.DISCOVER -> (if (isNew) 1f else 0f) + (1f - topic.coerceAtLeast(0f)) * 0.8f +
                recency * 0.3f + rand * 0.3f - seenPenalty - creatorFam.coerceAtLeast(0f) * 0.5f + interestMatch * 0.5f
            FeedMode.SMALL -> small * 2f + recency * 0.5f + topic.coerceAtLeast(0f) * 0.3f + rand * 0.2f - seenPenalty
            else -> w.topic * topic * 2f + interestMatch * 1.5f + w.creator * creatorFam + w.subscribed * subscribed +
                w.newCreators * (if (isNew) 0.6f else 0f) + w.popularity * pop + w.recency * recency +
                w.smallCreators * small * 0.6f + w.randomness * rand * 1.5f + length * 0.3f - seenPenalty
        }
        val why = buildList {
            if (interestMatch > 0f) add("Matches your selected interests")
            if (subscribed > 0) add("From a creator you follow")
            if (topic > 0.25f) add("Matches topics you watch")
            if (creatorFam > 0.3f) add("You've watched this creator before")
            if (mode == FeedMode.DISCOVER && isNew) add("New creator to you")
            if (mode == FeedMode.SMALL) add("Smaller creator")
            if (recency > 0.7f) add("New")
            if (isEmpty()) add("From ${mode.label} mode")
        }.take(3)
        return Ranked(v, s, why)
    }
}
