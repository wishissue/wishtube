package org.openvideo.aggregator.data

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.Request
import org.openvideo.aggregator.domain.Video
import org.openvideo.aggregator.sources.Http
import org.openvideo.aggregator.sources.SourceManager
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple local downloader. Only downloads a direct file URL that the SOURCE itself offers
 * (StreamInfo.downloadUrl). No DRM/paywall/stream-ripping workarounds: if a source gives no file URL,
 * the download fails with a clear message.
 * Limitation: runs in the app process; if Android kills the app the download is marked failed.
 */
class Downloads(
    ctx: Context, private val dao: AppDao, private val sources: SourceManager,
    private val library: LibraryRepository, private val notifier: Notifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val dir = File(ctx.filesDir, "downloads").apply { mkdirs() }

    init { scope.launch { dao.failStaleDownloads() } }

    fun start(video: Video) {
        if (jobs.containsKey(video.id)) return
        val job = scope.launch { run(video) }
        jobs[video.id] = job
        job.invokeOnCompletion { jobs.remove(video.id) }
    }

    fun cancel(videoId: String) { jobs[videoId]?.cancel() }

    suspend fun delete(videoId: String) {
        jobs.remove(videoId)?.cancelAndJoin()
        dao.download(videoId)?.let { File(it.path).delete() }
        dao.deleteDownload(videoId)
    }

    suspend fun deleteAll() { dao.downloadsNow().forEach { delete(it.videoId) } }

    private suspend fun run(video: Video) {
        val file = File(dir, video.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".mp4")
        val now = System.currentTimeMillis()
        try {
            library.cache(listOf(video))
            dao.putDownload(DownloadEntity(video.id, file.absolutePath, DlStatus.RUNNING, 0, 0, now))
            val s = sources.source(video.source).resolveStream(video)
            val url = s.downloadUrl ?: s.urls.firstOrNull()
                ?: throw IOException("${video.source.label} doesn't offer a downloadable file for this video.")
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L; var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            currentCoroutineContext().ensureActive()
                            if (read - last > 512 * 1024) { last = read; dao.updateProgress(video.id, read, total) }
                        }
                    }
                }
            }
            dao.putDownload(DownloadEntity(video.id, file.absolutePath, DlStatus.DONE, file.length(), file.length(), now))
            notifier.downloadDone(video, true)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { file.delete(); dao.deleteDownload(video.id) }
            throw e
        } catch (e: Exception) {
            file.delete()
            dao.putDownload(DownloadEntity(video.id, file.absolutePath, DlStatus.FAILED, 0, 0, now))
            notifier.downloadDone(video, false)
        }
    }
}
