package app.wishtube.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.wishtube.MainActivity
import app.wishtube.domain.Video

/** Local notifications only (download results, optional followed-creator updates). Never remote push. */
class Notifier(private val ctx: Context) {
    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_UPDATES, "Followed creators", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun downloadDone(video: Video, ok: Boolean) =
        post(video.id.hashCode(), CH_DOWNLOADS, if (ok) "Download complete" else "Download failed", video.title)

    fun newVideos(creator: String, count: Int, id: Int) =
        post(id, CH_UPDATES, creator, if (count == 1) "1 new video" else "$count new videos")

    @SuppressLint("MissingPermission")
    private fun post(id: Int, channel: String, title: String, text: String) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object { const val CH_DOWNLOADS = "downloads"; const val CH_UPDATES = "updates" }
}
