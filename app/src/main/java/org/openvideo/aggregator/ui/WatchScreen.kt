@file:Suppress("UnsafeOptInUsageError")

package org.openvideo.aggregator.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.openvideo.aggregator.data.DlStatus
import org.openvideo.aggregator.data.Kind
import org.openvideo.aggregator.data.UserPrefs
import org.openvideo.aggregator.data.toVideo
import org.openvideo.aggregator.domain.*
import org.openvideo.aggregator.recommend.Topics
import java.io.File
import java.util.Locale

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Applies the user's default quality, captions and speed to the shared player. */
private fun applyPlaybackPrefs(player: Player, up: UserPrefs) {
    val maxH = when (up.quality.value) { 0 -> Int.MAX_VALUE; 1 -> 1080; 2 -> 720; 3 -> 480; else -> 360 }
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setMaxVideoSize(Int.MAX_VALUE, maxH)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !up.captions.value)
        .setPreferredTextLanguage(Locale.getDefault().language)
        .build()
    player.setPlaybackSpeed(up.speed.value)
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun WatchScreen(videoId: String, vm: AppViewModel, pad: PaddingValues, onBack: () -> Unit, onOpenVideo: (Video) -> Unit) {
    val c = vm.container
    val video by produceState<Video?>(c.sources.known[videoId], videoId) {
        val vLoaded = c.sources.known[videoId] ?: c.dao.video(videoId)?.toVideo()
        value = vLoaded
    }
    val v = video
    if (v == null) {
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { FunLoader("Loading video...") }
        return
    }
    WatchContent(v, vm, pad, onBack, onOpenVideo)
}

@Composable
private fun WatchContent(video: Video, vm: AppViewModel, pad: PaddingValues, onBack: () -> Unit, onOpenVideo: (Video) -> Unit) {
    val ctx = LocalContext.current
    val c = vm.container
    val exo = c.player
    val cfg = LocalConfiguration.current
    val inPip by c.inPip.collectAsStateWithLifecycle()
    val gestures by vm.prefs.gestures.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val landscapePhone = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && cfg.screenHeightDp < 500
    val immersive = fullscreen || landscapePhone || inPip
    val wide = cfg.screenWidthDp >= 840 && !immersive
    var resolving by remember(video.id) { mutableStateOf(true) }
    var loadError by remember(video.id) { mutableStateOf<String?>(null) }
    var attempt by remember(video.id) { mutableIntStateOf(0) }
    var streamIndex by remember(video.id) { mutableIntStateOf(0) }
    var extra by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    val hidden by vm.hiddenCreators.collectAsStateWithLifecycle()
    val related = remember(video.id) {
        val t = Topics.of(video)
        vm.container.sources.known.values.filter {
            it.id != video.id && it.creatorKey !in hidden &&
                (it.creatorKey == video.creatorKey || Topics.of(it).intersect(t).isNotEmpty())
        }.take(12)
    }

    DisposableEffect(Unit) { c.pipEligible = true; onDispose { c.pipEligible = false } }

    // System bars / orientation for fullscreen & landscape.
    DisposableEffect(immersive, fullscreen) {
        val act = ctx.findActivity()
        val ctrl = act?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (fullscreen) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (immersive) {
            ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        } else ctrl?.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (fullscreen) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val handleBack: () -> Unit = {
        if (fullscreen) {
            fullscreen = false
            ctx.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ctx.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onBack()
        }
    }
    BackHandler(onBack = handleBack)

    // Resolve the stream and start playback. Downloaded files play offline without any network call.
    LaunchedEffect(video.id, attempt, streamIndex) {
        val playing = vm.nowPlaying.value
        if (playing?.id == video.id && attempt == 0 && streamIndex == 0 && exo.playbackState != Player.STATE_IDLE) {
            resolving = false; return@LaunchedEffect
        }
        if (playing != null && playing.id != video.id) vm.saveProgress(playing, exo.currentPosition, exo.duration)
        resolving = true; loadError = null; playerError = null
        try {
            val dl = c.dao.download(video.id)
            val local = dl?.takeIf { it.status == DlStatus.DONE && File(it.path).exists() }
            val s: StreamInfo? = if (local != null) null else c.sources.source(video.source).resolveStream(video)
            extra = s
            val targetUrl = if (local != null) Uri.fromFile(File(local.path)).toString() else {
                val urls = s?.urls.orEmpty()
                urls.getOrNull(streamIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) ?: s?.url ?: throw IOException("No stream URL available")
            }
            val uri = Uri.parse(targetUrl)
            val item = MediaItem.Builder().setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).setArtist(video.creator)
                    .setArtworkUri(video.thumbnail?.let { Uri.parse(it) }).build())
                .setSubtitleConfigurations(s?.captions.orEmpty().map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url)).setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(it.lang).setLabel(it.label).build()
                }).build()
            val resume = vm.resumePosition(video)
            applyPlaybackPrefs(exo, vm.prefs)
            exo.setMediaItem(item)
            exo.prepare()
            if (resume > 0) exo.seekTo(resume)
            exo.playWhenReady = vm.prefs.autoplay.value
            vm.nowPlaying.value = video
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = e.message ?: "Unknown error" }
        resolving = false
    }

    // Periodic progress save + final save when leaving.
    LaunchedEffect(video.id) {
        while (true) {
            delay(10_000)
            if (exo.isPlaying && vm.nowPlaying.value?.id == video.id) vm.saveProgress(video, exo.currentPosition, exo.duration)
        }
    }
    DisposableEffect(video.id) {
        onDispose { if (vm.nowPlaying.value?.id == video.id) vm.saveProgress(video, exo.currentPosition, exo.duration) }
    }
    DisposableEffect(exo, related, extra) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val s = extra
                if (s != null && streamIndex + 1 < s.urls.size) {
                    streamIndex++
                    attempt++
                } else {
                    playerError = "Playback failed (code ${error.errorCode}: ${error.message ?: error::class.java.simpleName})"
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
                if (playbackState == Player.STATE_ENDED && vm.prefs.autoplayNext.value) {
                    related.firstOrNull()?.let { onOpenVideo(it) }
                }
            }
        }
        exo.addListener(l); onDispose { exo.removeListener(l) }
    }

    val retry: () -> Unit = {
        streamIndex = 0
        attempt++
    }
    val playerBox: @Composable (Modifier) -> Unit = { m ->
        PlayerArea(video, exo, m, resolving, loadError ?: playerError, gestures, retry, fullscreen, { fullscreen = it }, onBack)
    }

    when {
        immersive -> Box(Modifier.fillMaxSize().background(Color.Black)) { playerBox(Modifier.fillMaxSize()) }
        wide -> Row(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1.6f).padding(16.dp)) { playerBox(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) }
            Details(video, extra, vm, Modifier.weight(1f), onOpenVideo, exo)
        }
        else -> Column(Modifier.fillMaxSize().padding(pad)) {
            playerBox(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            Details(video, extra, vm, Modifier.weight(1f), onOpenVideo, exo)
        }
    }
}

@Composable
private fun PlayerArea(
    video: Video, exo: Player, modifier: Modifier, resolving: Boolean, error: String?, gestures: Boolean,
    onRetry: () -> Unit, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exo
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxSize(),
        )

        // Top WishTube Liquid Glass Bar Overlay
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                }
            ) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            GlassSurface(
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFullscreen(!fullscreen)
                }
            ) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        "Fullscreen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (resolving) {
            Box(Modifier.fillMaxSize().background(Color(0xCC1C1917)), contentAlignment = Alignment.Center) {
                FunLoader("Brewing your stream...")
            }
        }

        if (error != null) {
            GlassSurface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text("Couldn't play this media", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onRetry, shape = RoundedCornerShape(20.dp)) { Text("Retry") }
                        OutlinedButton(onClick = { openUrl(ctx, video.sourceUrl) }, shape = RoundedCornerShape(20.dp)) { Text("Open on ${video.source.label}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(video: Video, extra: StreamInfo?, vm: AppViewModel, modifier: Modifier, onOpenVideo: (Video) -> Unit, exo: Player) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val nav = LocalNav.current
    val flags by remember(video.id) { vm.flagsFor(video.id) }.collectAsState(emptySet<String>())
    val note by remember(video.id) { vm.noteFor(video.id) }.collectAsState(null)
    val follows by vm.follows.collectAsStateWithLifecycle()
    val hidden by vm.hiddenCreators.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val downloads by vm.downloadMap.collectAsStateWithLifecycle()
    var expanded by remember(video.id) { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var plDialog by remember { mutableStateOf(false) }
    val following = follows.any { it.creatorKey == video.creatorKey }
    val description = extra?.description?.takeIf { it.isNotBlank() } ?: video.description
    val chapters = remember(video.id, extra) { extra?.chapters?.takeIf { it.isNotEmpty() } ?: parseChapters(description) }
    val canDownload = vm.container.sources.source(video.source).capabilities.downloads
    val dl = downloads[video.id]
    val related = remember(video.id) {
        val t = Topics.of(video)
        vm.container.sources.known.values.filter {
            it.id != video.id && it.creatorKey !in hidden &&
                (it.creatorKey == video.creatorKey || Topics.of(it).intersect(t).isNotEmpty())
        }.take(12)
    }
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(video.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(video.metaLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClickLabel = "Open creator page") { nav?.openCreator(video) }) {
                    Text(video.creator, style = MaterialTheme.typography.titleMedium)
                    Text("on ${video.source.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (following) FilledTonalButton({ vm.toggleFollow(video) }) { Text("Following") }
                else Button({ vm.toggleFollow(video) }) { Text("Follow") }
            }
            Text("Follow is local to this profile and does not subscribe you on ${video.source.label}.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(Kind.LIKED in flags, { tick(); vm.toggle(video, Kind.LIKED) }, { Text("Like") }, leadingIcon = { Icon(Icons.Outlined.ThumbUp, null, Modifier.size(18.dp)) })
                FilterChip(Kind.SAVED in flags, { tick(); vm.toggle(video, Kind.SAVED) }, { Text("Save") }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)) })
                FilterChip(Kind.WATCH_LATER in flags, { tick(); vm.toggle(video, Kind.WATCH_LATER) }, { Text("Later") }, leadingIcon = { Icon(Icons.Outlined.WatchLater, null, Modifier.size(18.dp)) })
                AssistChip({ plDialog = true }, { Text("Playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(18.dp)) })
                AssistChip({ noteDialog = true }, { Text("Note") }, leadingIcon = { Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)) })
                if (canDownload) when (dl?.status) {
                    DlStatus.DONE -> FilterChip(true, { vm.deleteDownload(video.id) }, { Text("Downloaded") }, leadingIcon = { Icon(Icons.Outlined.DownloadDone, null, Modifier.size(18.dp)) })
                    DlStatus.RUNNING -> AssistChip({ vm.cancelDownload(video.id) },
                        { val total = dl?.total ?: 0L; Text(if (total > 0) "Downloading ${100 * (dl?.bytes ?: 0L) / total}% (cancel)" else "Downloading… (cancel)") },
                        leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) })
                    else -> AssistChip({ vm.startDownload(video) }, { Text(if (dl?.status == DlStatus.FAILED) "Retry download" else "Download") },
                        leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) })
                }
                AssistChip({ ctx.findActivity()?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) },
                    { Text("PiP") }, leadingIcon = { Icon(Icons.Outlined.PictureInPictureAlt, null, Modifier.size(18.dp)) })
                AssistChip({ shareUrl(ctx, video) }, { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) })
                AssistChip({ openUrl(ctx, video.sourceUrl) }, { Text("Original") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)) })
                AssistChip({ vm.notInterested(video) }, { Text("Not interested") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp)) })
            }
        }
        if (description.isNotBlank()) item {
            GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClickLabel = if (expanded) "Collapse description" else "Expand description") { expanded = !expanded }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Description from ${video.source.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(description, maxLines = if (expanded) Int.MAX_VALUE else 3, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        note?.let { n ->
            item {
                GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit note") { noteDialog = true }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("My note (private)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text(n, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (chapters.isNotEmpty()) {
            item { Text("Chapters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            items(chapters) { ch ->
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Jump to ${ch.title}") { exo.seekTo(ch.startSec * 1000L) }.padding(vertical = 8.dp)) {
                    Text(formatDuration(ch.startSec.toLong()).ifEmpty { "0:00" }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                    Text(ch.title)
                }
            }
        }
        if (related.isNotEmpty()) {
            item { Text("Related", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            items(related, key = { it.id }) { r -> VideoCard(r, vm, { onOpenVideo(r) }) }
        }
    }

    if (noteDialog) {
        var text by remember(note) { mutableStateOf(note.orEmpty()) }
        AlertDialog(onDismissRequest = { noteDialog = false }, title = { Text("Private note") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, label = { Text("Only stored on this device") }) },
            confirmButton = { TextButton({ vm.saveNote(video.id, text); noteDialog = false }) { Text("Save") } },
            dismissButton = { TextButton({ noteDialog = false }) { Text("Cancel") } })
    }
    if (plDialog) {
        var creating by remember { mutableStateOf(false) }
        if (creating) NameDialog("New playlist", { creating = false }) { vm.createPlaylist(it); creating = false }
        else AlertDialog(onDismissRequest = { plDialog = false }, title = { Text("Add to playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) Text("No playlists yet.")
                    playlists.forEach { p -> TextButton({ vm.addToPlaylist(p.id, video); plDialog = false }) { Text(p.name) } }
                }
            },
            confirmButton = { TextButton({ creating = true }) { Text("New playlist") } },
            dismissButton = { TextButton({ plDialog = false }) { Text("Close") } })
    }
}
