package app.wishtube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wishtube.data.DlStatus
import app.wishtube.data.PlaylistEntity
import app.wishtube.data.toVideo
import app.wishtube.domain.Video

private val tabTitles = listOf("History", "Watch Later", "Saved", "Liked", "Playlists", "Downloads", "Notes")

@Composable
fun LibraryScreen(app: AppViewModel, pad: PaddingValues, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<PlaylistEntity?>(null) }
    val history by app.history.collectAsStateWithLifecycle()
    val later by app.watchLater.collectAsStateWithLifecycle()
    val saved by app.saved.collectAsStateWithLifecycle()
    val liked by app.liked.collectAsStateWithLifecycle()
    val playlists by app.playlists.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(pad)) {
        ScreenTopBar("Library", nav)
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabTitles.forEachIndexed { i, t -> Tab(tab == i, { tab = i; open = null }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> VideoList(history, app, nav, Icons.Outlined.History, "No history yet", "Videos you watch will appear here.")
            1 -> VideoList(later, app, nav, Icons.Outlined.WatchLater, "Nothing saved for later", "Videos you add to Watch Later will appear here.")
            2 -> VideoList(saved, app, nav, Icons.Outlined.BookmarkBorder, "Nothing here yet", "Videos you save will appear here.")
            3 -> VideoList(liked, app, nav, Icons.Outlined.ThumbUp, "No local likes", "Likes are private to this profile and help your recommendations.")
            4 -> {
                val pl = open
                if (pl != null) {
                    val vids by remember(pl.id) { app.playlistVideos(pl.id) }.collectAsState(emptyList<Video>())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ open = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to playlists") }
                        Text(pl.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton({ app.deletePlaylist(pl.id); open = null }) { Icon(Icons.Outlined.Delete, "Delete playlist") }
                    }
                    VideoList(vids, app, nav, Icons.AutoMirrored.Outlined.PlaylistPlay, "Empty playlist", "Add videos from a video page.")
                } else PlaylistList(playlists, app) { open = it }
            }
            5 -> DownloadsTab(app, nav)
            else -> NotesTab(app, nav)
        }
    }
}

@Composable
private fun VideoList(items: List<Video>, app: AppViewModel, nav: Nav, icon: ImageVector, title: String, body: String) {
    if (items.isEmpty()) { EmptyState(icon, title, body); return }
    LazyVerticalGrid(feedCells(), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(feedSpacing())) {
        items(items, key = { it.id }) { v -> VideoCard(v, app, { nav.openVideo(v) }, Modifier.animateItem()) }
    }
}

@Composable
private fun DownloadsTab(app: AppViewModel, nav: Nav) {
    val vids by app.downloadedVideos.collectAsStateWithLifecycle()
    val map by app.downloadMap.collectAsStateWithLifecycle()
    if (vids.isEmpty()) {
        EmptyState(Icons.Outlined.Download, "No downloads", "Downloads appear here when a source offers a downloadable file. They play offline.")
        return
    }
    LazyVerticalGrid(feedCells(), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vids, key = { it.id }) { v ->
            val d = map[v.id]
            Column(Modifier.animateItem()) {
                VideoCard(v, app, { nav.openVideo(v) })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    val status = when (d?.status) {
                        DlStatus.DONE -> "Downloaded • ${"%.1f".format((d?.bytes ?: 0L) / 1_048_576.0)} MB"
                        DlStatus.RUNNING -> if ((d?.total ?: 0L) > 0) "Downloading ${100 * (d?.bytes ?: 0L) / (d?.total ?: 1L)}%" else "Downloading…"
                        DlStatus.FAILED -> "Failed"
                        else -> ""
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium,
                        color = if (d?.status == DlStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    if (d?.status == DlStatus.RUNNING) TextButton({ app.cancelDownload(v.id) }) { Text("Cancel") }
                    else {
                        if (d?.status == DlStatus.FAILED) TextButton({ app.startDownload(v) }) { Text("Retry") }
                        TextButton({ app.deleteDownload(v.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(app: AppViewModel, nav: Nav) {
    val rows by app.notes.collectAsStateWithLifecycle()
    if (rows.isEmpty()) {
        EmptyState(Icons.Outlined.EditNote, "No notes yet", "Private notes you add to videos appear here. They never change the original description.")
        return
    }
    LazyVerticalGrid(feedCells(), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows, key = { it.video.id }) { r ->
            val v = remember(r.video.id) { r.video.toVideo() }
            Column(Modifier.animateItem()) {
                VideoCard(v, app, { nav.openVideo(v) })
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("My note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(r.noteText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(items: List<PlaylistEntity>, app: AppViewModel, onOpen: (PlaylistEntity) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) EmptyState(Icons.AutoMirrored.Outlined.PlaylistPlay, "No playlists", "Create one to organise videos from any source.", action = "New playlist", onAction = { dialog = true })
        else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(items, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) }, leadingContent = { Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null) },
                    modifier = Modifier.clickable { onOpen(p) })
            }
        }
        if (items.isNotEmpty()) ExtendedFloatingActionButton(
            onClick = { dialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New playlist") })
    }
    if (dialog) NameDialog("New playlist", { dialog = false }) { app.createPlaylist(it); dialog = false }
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onOk(text) }, enabled = text.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
