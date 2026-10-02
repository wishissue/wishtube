package org.openvideo.aggregator.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.openvideo.aggregator.data.Kind
import org.openvideo.aggregator.domain.SourceId
import org.openvideo.aggregator.domain.Video
import org.openvideo.aggregator.domain.creatorKey
import org.openvideo.aggregator.recommend.Topics
import org.openvideo.aggregator.ui.theme.LocalReduceMotion

class Nav(
    val openVideo: (Video) -> Unit, val openCreator: (Video) -> Unit,
    val search: () -> Unit, val settings: () -> Unit, val library: () -> Unit,
)

val LocalNav = staticCompositionLocalOf<Nav?> { null }
val LocalCompact = staticCompositionLocalOf { false }

/** Opens a URL in the default BROWSER (never loops back into this app's own deep-link filters). */
fun openUrl(ctx: Context, url: String) {
    val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        .setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

/** Shares the ORIGINAL source URL, never an intermediary link. */
fun shareUrl(ctx: Context, v: Video) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.sourceUrl)
    runCatching { ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun feedCells(): GridCells = GridCells.Adaptive(if (LocalCompact.current) 420.dp else 340.dp)

@Composable
fun feedSpacing(): Dp = if (LocalCompact.current) 4.dp else 12.dp

@Composable
fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    if (LocalReduceMotion.current) return SolidColor(base)
    val hi = MaterialTheme.colorScheme.outlineVariant
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1400f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x - 400f, 0f), end = Offset(x, 200f))
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp)).background(brush))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
    }
}

@Composable
fun VideoCard(video: Video, vm: AppViewModel, onClick: () -> Unit, modifier: Modifier = Modifier, reason: String? = null) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val compact = LocalCompact.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }

    @Composable
    fun Thumb(m: Modifier) {
        Box(m.aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val d = formatDuration(video.durationSec)
            if (d.isNotEmpty()) Text(d, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }

    @Composable
    fun Texts(m: Modifier) {
        Column(m) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.creator, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (nav != null) Modifier.clickable(onClickLabel = "Open creator ${video.creator}") { nav.openCreator(video) } else Modifier)
            Text(video.metaLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (reason != null) Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }

    @Composable
    fun MenuButton() {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options for ${video.title}") }
            DropdownMenu(menu, { menu = false }) {
                @Composable
                fun Item(label: String, icon: ImageVector, act: () -> Unit) = DropdownMenuItem(
                    text = { Text(label) }, leadingIcon = { Icon(icon, null) },
                    onClick = { menu = false; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); act() })
                Item("Watch Later", Icons.Outlined.WatchLater) { vm.toggle(video, Kind.WATCH_LATER) }
                Item("Save", Icons.Outlined.BookmarkBorder) { vm.toggle(video, Kind.SAVED) }
                Item("Like locally", Icons.Outlined.ThumbUp) { vm.toggle(video, Kind.LIKED) }
                Item("Not interested", Icons.Outlined.VisibilityOff) { vm.notInterested(video) }
                Item("Hide creator", Icons.Outlined.PersonOff) { vm.hideCreator(video) }
                val topic = Topics.primary(video)
                if (topic != null) Item("Hide topic “$topic”", Icons.Outlined.Block) { vm.hideTopic(topic) }
                Item("Open original", Icons.AutoMirrored.Outlined.OpenInNew) { openUrl(ctx, video.sourceUrl) }
                Item("Share", Icons.Outlined.Share) { shareUrl(ctx, video) }
            }
        }
    }

    val shape = RoundedCornerShape(16.dp)
    if (compact) {
        Row(modifier.fillMaxWidth().clip(shape).clickable(onClickLabel = "Watch ${video.title}", onClick = onClick).padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top) {
            Thumb(Modifier.width(150.dp))
            Texts(Modifier.weight(1f).padding(start = 12.dp))
            MenuButton()
        }
    } else {
        Column(modifier.fillMaxWidth().clip(shape).clickable(onClickLabel = "Watch ${video.title}", onClick = onClick).padding(bottom = 6.dp)) {
            Thumb(Modifier.fillMaxWidth())
            Row(Modifier.padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                Texts(Modifier.weight(1f))
                MenuButton()
            }
        }
    }
}

@Composable
fun ScreenTopBar(title: String, nav: Nav?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        if (nav != null) {
            IconButton(onClick = nav.search) { Icon(Icons.Outlined.Search, "Search") }
            IconButton(onClick = nav.settings) { Icon(Icons.Outlined.Settings, "Settings") }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); FilledTonalButton(onClick = onAction) { Text(action) } }
    }
}

fun failureText(errors: Map<SourceId, String>): String =
    errors.keys.joinToString(" and ") { it.label } + " could not be reached."

@Composable
fun ErrorBanner(errors: Map<SourceId, String>, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(failureText(errors), style = MaterialTheme.typography.bodyMedium)
                Text("Other sources and your local library still work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Full-screen failure state: distinguishes "you're offline" from "a source is down". */
@Composable
fun FailureState(errors: Map<SourceId, String>, nav: Nav?, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    if (!isOnline(ctx)) EmptyState(Icons.Outlined.CloudOff, "You're offline", "Your local library is still available.",
        action = if (nav != null) "View Library" else "Retry", onAction = { if (nav != null) nav.library() else onRetry() })
    else EmptyState(Icons.Outlined.CloudOff, failureText(errors), "Your local library is still available.", action = "Retry", onAction = onRetry)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val brush = remember(surfaceColor, primaryColor) {
        Brush.linearGradient(
            colors = listOf(
                surfaceColor.copy(alpha = 0.90f),
                surfaceColor.copy(alpha = 0.78f),
                primaryColor.copy(alpha = 0.12f)
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }
    val borderBrush = remember(primaryColor) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                primaryColor.copy(alpha = 0.3f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(500f, 500f)
        )
    }

    if (onClick != null) {
        Surface(
            modifier = modifier.border(BorderStroke(1.5.dp, borderBrush), shape),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 10.dp,
            shadowElevation = 10.dp,
            onClick = onClick
        ) {
            Box(modifier = Modifier.background(brush), content = content)
        }
    } else {
        Surface(
            modifier = modifier.border(BorderStroke(1.5.dp, borderBrush), shape),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 10.dp,
            shadowElevation = 10.dp
        ) {
            Box(modifier = Modifier.background(brush), content = content)
        }
    }
}

@Composable
fun FunLoader(message: String = "Loading magic...") {
    val t = rememberInfiniteTransition(label = "loader")
    val scale by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val alpha by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha")

    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(72.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    }
}
