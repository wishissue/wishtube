package org.openvideo.aggregator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.openvideo.aggregator.domain.CreatorInfo
import org.openvideo.aggregator.domain.SourceId
import org.openvideo.aggregator.domain.Video

/** key = "<SOURCE>:<creatorId>", e.g. "PEERTUBE:channel@host.tld". */
@Composable
fun CreatorScreen(key: String, app: AppViewModel, pad: PaddingValues, nav: Nav, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val source = remember(key) { runCatching { SourceId.valueOf(key.substringBefore(':')) }.getOrNull() }
    val creatorId = remember(key) { key.substringAfter(':') }
    if (source == null) { EmptyState(Icons.Outlined.VideoLibrary, "Unknown creator", "This creator link isn't valid.", Modifier.padding(pad)); return }

    var info by remember(key) { mutableStateOf<CreatorInfo?>(null) }
    var videos by remember(key) { mutableStateOf<List<Video>?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    val follows by app.follows.collectAsStateWithLifecycle()
    val hidden by app.hiddenCreators.collectAsStateWithLifecycle()
    val following = follows.any { it.creatorKey == key }

    LaunchedEffect(key, attempt) {
        error = null
        info = app.container.sources.creatorInfo(source, creatorId)
        val r = app.container.sources.creators(listOf(source to creatorId))
        videos = r.videos
        error = r.errors.values.firstOrNull()
    }

    val first = videos?.firstOrNull()
    val name = info?.name ?: first?.creator ?: follows.firstOrNull { it.creatorKey == key }?.name ?: "Creator"
    val originalUrl = info?.url ?: first?.sourceUrl?.substringBeforeLast('/')

    Column(Modifier.fillMaxSize().padding(pad)) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
        }
        LazyVerticalGrid(feedCells(), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(feedSpacing())) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (info?.avatar != null) AsyncImage(info?.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            val followers = info?.followers?.let { "$it followers on ${source.label}" }
                            Text(listOfNotNull("on ${source.label}", followers).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    info?.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (following) FilledTonalButton({ app.toggleFollow(key, name, source, creatorId) }) { Text("Following") }
                        else Button({ app.toggleFollow(key, name, source, creatorId) }) { Text("Follow") }
                        if (originalUrl != null) OutlinedButton({ openUrl(ctx, originalUrl) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open on ${source.label}")
                        }
                    }
                    Text("Follow is local to this profile. It does not subscribe you on ${source.label}.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val list = if (key in hidden) emptyList() else videos.orEmpty()
            when {
                videos == null -> items(4) { SkeletonCard() }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (error != null) "${source.label} could not be reached." else "No videos found.", style = MaterialTheme.typography.titleMedium)
                        if (error != null) TextButton({ attempt++ }) { Text("Retry") }
                    }
                }
                else -> items(list, key = { it.id }) { v -> VideoCard(v, app, { nav.openVideo(v) }, Modifier.animateItem()) }
            }
        }
    }
}
