package org.openvideo.aggregator.data

import android.content.Context
import androidx.work.*
import org.openvideo.aggregator.App
import org.openvideo.aggregator.domain.SourceId
import java.util.concurrent.TimeUnit

/** Optional: checks followed creators a few times a day and posts a LOCAL notification. No server involved. */
class CreatorUpdatesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as App).container
        for (profile in c.dao.profilesNow()) {
            for (f in c.dao.followsNow(profile.id).take(20)) {
                val sid = SourceId.valueOf(f.source)
                if (sid !in c.sources.enabled.value) continue
                val vids = runCatching { c.sources.source(sid).creatorVideos(f.creatorId, 0) }.getOrNull() ?: continue
                val newest = vids.maxOfOrNull { it.uploadDate } ?: continue
                val key = "seen_${profile.id}_${f.creatorKey}"
                val seen = c.prefs.getLong(key, -1L)
                if (seen == -1L) { c.prefs.edit().putLong(key, newest).apply(); continue }  // first run = baseline
                val fresh = vids.count { it.uploadDate > seen }
                if (fresh > 0) {
                    c.notifier.newVideos(f.name, fresh, key.hashCode())
                    c.prefs.edit().putLong(key, newest).apply()
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "creator-updates"
        fun schedule(ctx: Context, on: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!on) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<CreatorUpdatesWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
