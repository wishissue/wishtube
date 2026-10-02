package app.wishtube.ui

import android.text.format.DateUtils
import app.wishtube.domain.Video

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatViews(v: Long?): String? = v?.let {
    when {
        it >= 1_000_000 -> "%.1fM views".format(it / 1_000_000.0)
        it >= 1_000 -> "%.1fK views".format(it / 1_000.0)
        else -> "$it views"
    }
}

fun relativeTime(ms: Long): String? =
    if (ms <= 0) null
    else DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** "PeerTube • 12K views • 2 days ago" – source is always visible. */
fun Video.metaLine(): String = listOfNotNull(source.label, formatViews(viewCount), relativeTime(uploadDate)).joinToString(" • ")
