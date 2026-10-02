package org.openvideo.aggregator.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import org.openvideo.aggregator.domain.FeedMode
import org.openvideo.aggregator.domain.Video
import org.openvideo.aggregator.domain.creatorKey
import org.openvideo.aggregator.ui.theme.OvaTheme

@Composable
fun MainApp(vm: AppViewModel = viewModel()) {
    val theme by vm.prefs.theme.collectAsStateWithLifecycle()
    val reduce by vm.prefs.reduceMotion.collectAsStateWithLifecycle()
    val dynamic by vm.prefs.dynamicColor.collectAsStateWithLifecycle()
    val compact by vm.prefs.compact.collectAsStateWithLifecycle()
    OvaTheme(theme, reduce, dynamic) {
        CompositionLocalProvider(LocalCompact provides compact) { AppScaffold(vm, reduce) }
    }
}

@Composable
private fun rememberGlassEnabled(vm: AppViewModel): Boolean {
    val ctx = LocalContext.current
    val glassPref by vm.prefs.glassEffects.collectAsStateWithLifecycle()
    val reduce by vm.prefs.reduceMotion.collectAsStateWithLifecycle()
    return remember(glassPref, reduce) {
        if (!glassPref || reduce || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) false
        else {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode != true
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)
private val mainTabs = listOf(
    TabItem("home", "Home", Icons.Outlined.Home), TabItem("discover", "Discover", Icons.Outlined.Explore),
    TabItem("following", "Following", Icons.Outlined.Subscriptions), TabItem("library", "Library", Icons.Outlined.VideoLibrary),
)

@Composable
private fun AppScaffold(vm: AppViewModel, reduce: Boolean) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = mainTabs.any { it.route == route }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val snack = remember { SnackbarHostState() }
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val deepLink by vm.container.deepLink.collectAsStateWithLifecycle()
    val glassEnabled = rememberGlassEnabled(vm)
    LaunchedEffect(Unit) { vm.messages.collectLatest { snack.showSnackbar(it) } }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    val actions = remember(nav) {
        Nav(
            openVideo = { nav.navigate("watch/" + Uri.encode(it.id)) },
            openCreator = { nav.navigate("creator/" + Uri.encode(it.creatorKey)) },
            search = { nav.navigate("search") }, settings = { nav.navigate("settings") },
            library = {
                nav.navigate("library") {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            })
    }

    // Deep links: open supported PeerTube/Odysee URLs in the unified watch page, else fall back to the browser.
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        vm.container.deepLink.value = null
        val v = vm.container.sources.resolveUrl(link)
        if (v != null) actions.openVideo(v) else { vm.messages.tryEmit("Can't open this link here. Opening in browser."); openUrl(ctx, link) }
    }

    CompositionLocalProvider(LocalNav provides actions) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    val np = nowPlaying
                    if (np != null && route?.startsWith("watch") != true)
                        MiniPlayer(np, vm.container.player, { actions.openVideo(np) }, { vm.closePlayer() }, glassEnabled)
                    if (isTab && !wide) {
                        if (glassEnabled) {
                            GlassSurface(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                NavigationBar(containerColor = Color.Transparent) {
                                    mainTabs.forEach { t -> NavigationBarItem(route == t.route, { goTab(t.route) }, { Icon(t.icon, null) }, label = { Text(t.label) }) }
                                }
                            }
                        } else {
                            NavigationBar {
                                mainTabs.forEach { t -> NavigationBarItem(route == t.route, { goTab(t.route) }, { Icon(t.icon, null) }, label = { Text(t.label) }) }
                            }
                        }
                    }
                }
            },
        ) { pad ->
            Row(Modifier.fillMaxSize()) {
                if (isTab && wide) {
                    if (glassEnabled) {
                        GlassSurface(modifier = Modifier.padding(8.dp).fillMaxHeight(), shape = RoundedCornerShape(24.dp)) {
                            NavigationRail(containerColor = Color.Transparent) {
                                Spacer(Modifier.weight(1f))
                                mainTabs.forEach { t -> NavigationRailItem(route == t.route, { goTab(t.route) }, { Icon(t.icon, null) }, label = { Text(t.label) }) }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        NavigationRail {
                            Spacer(Modifier.weight(1f))
                            mainTabs.forEach { t -> NavigationRailItem(route == t.route, { goTab(t.route) }, { Icon(t.icon, null) }, label = { Text(t.label) }) }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                NavHost(
                    nav, startDestination = "home", modifier = Modifier.weight(1f),
                    enterTransition = { if (reduce) EnterTransition.None else fadeIn(tween(220)) },
                    exitTransition = { if (reduce) ExitTransition.None else fadeOut(tween(160)) },
                    popEnterTransition = { if (reduce) EnterTransition.None else fadeIn(tween(220)) },
                    popExitTransition = { if (reduce) ExitTransition.None else fadeOut(tween(160)) },
                ) {
                    composable("home") { FeedScreen("OpenVideo", listOf(FeedMode.FOR_YOU, FeedMode.NEW, FeedMode.CHRONOLOGICAL), "home", vm, pad, actions) }
                    composable("discover") { FeedScreen("Discover", listOf(FeedMode.DISCOVER, FeedMode.SMALL, FeedMode.RANDOM), "discover", vm, pad, actions) }
                    composable("following") { FeedScreen("Following", listOf(FeedMode.FOLLOWING), "following", vm, pad, actions) }
                    composable("library") { LibraryScreen(vm, pad, actions) }
                    composable("search") { SearchScreen(vm, pad, actions) { nav.popBackStack() } }
                    composable("settings") { SettingsScreen(vm, pad) { nav.popBackStack() } }
                    composable("creator/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
                        CreatorScreen(e.arguments?.getString("key").orEmpty(), vm, pad, actions) { nav.popBackStack() }
                    }
                    composable("watch/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                        WatchScreen(e.arguments?.getString("id").orEmpty(), vm, pad, { nav.popBackStack() }) { actions.openVideo(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(video: Video, player: Player, onOpen: () -> Unit, onClose: () -> Unit, glassEnabled: Boolean) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }
        player.addListener(l); onDispose { player.removeListener(l) }
    }
    val content = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            AsyncImage(video.thumbnail, null, Modifier.width(96.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(video.source.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ if (playing) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play")
            }
            IconButton(onClose) { Icon(Icons.Outlined.Close, "Close player") }
        }
    }
    if (glassEnabled) {
        GlassSurface(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
                .clickable(onClickLabel = "Open player for ${video.title}", onClick = onOpen),
            shape = RoundedCornerShape(20.dp)
        ) {
            content()
        }
    } else {
        Surface(
            tonalElevation = 3.dp, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
                .clickable(onClickLabel = "Open player for ${video.title}", onClick = onOpen)
        ) {
            content()
        }
    }
}
