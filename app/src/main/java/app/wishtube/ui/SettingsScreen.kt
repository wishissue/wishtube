package app.wishtube.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wishtube.data.CreatorUpdatesWorker
import app.wishtube.recommend.RecWeights

private class WSpec(val label: String, val help: String, val get: (RecWeights) -> Float, val set: (RecWeights, Float) -> RecWeights)

private val specs = listOf(
    WSpec("Topic similarity", "Prefer videos about subjects you already watch.", { it.topic }, { w, v -> w.copy(topic = v) }),
    WSpec("Creator familiarity", "Prefer creators you have watched before.", { it.creator }, { w, v -> w.copy(creator = v) }),
    WSpec("New creators", "Mix in creators you haven't seen yet.", { it.newCreators }, { w, v -> w.copy(newCreators = v) }),
    WSpec("Popularity", "Prefer videos with more views (when the source reports views).", { it.popularity }, { w, v -> w.copy(popularity = v) }),
    WSpec("Recency", "Prefer recently published videos.", { it.recency }, { w, v -> w.copy(recency = v) }),
    WSpec("Small creators", "Prefer creators with smaller audiences.", { it.smallCreators }, { w, v -> w.copy(smallCreators = v) }),
    WSpec("Followed creators", "Boost creators you follow locally.", { it.subscribed }, { w, v -> w.copy(subscribed = v) }),
    WSpec("Randomness", "Add surprise so the feed doesn't get stale.", { it.randomness }, { w, v -> w.copy(randomness = v) }),
    WSpec("Long videos", "Boost videos over 20 minutes.", { it.longVideos }, { w, v -> w.copy(longVideos = v) }),
    WSpec("Short videos", "Boost videos under 5 minutes.", { it.shortVideos }, { w, v -> w.copy(shortVideos = v) }),
)

@Composable
fun SettingsScreen(app: AppViewModel, pad: PaddingValues, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val up = app.prefs
    val container = app.container
    val theme by up.theme.collectAsStateWithLifecycle()
    val dynamic by up.dynamicColor.collectAsStateWithLifecycle()
    val compact by up.compact.collectAsStateWithLifecycle()
    val reduce by up.reduceMotion.collectAsStateWithLifecycle()
    val glassEffects by up.glassEffects.collectAsStateWithLifecycle()
    val showMature by up.showMature.collectAsStateWithLifecycle()
    val autoplay by up.autoplay.collectAsStateWithLifecycle()
    val autoplayNext by up.autoplayNext.collectAsStateWithLifecycle()
    val captions by up.captions.collectAsStateWithLifecycle()
    val background by up.background.collectAsStateWithLifecycle()
    val pip by up.pip.collectAsStateWithLifecycle()
    val gestures by up.gestures.collectAsStateWithLifecycle()
    val quality by up.quality.collectAsStateWithLifecycle()
    val speed by up.speed.collectAsStateWithLifecycle()
    val updates by up.creatorUpdates.collectAsStateWithLifecycle()
    val weights by app.weights.collectAsStateWithLifecycle()
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val current by app.profile.collectAsStateWithLifecycle()
    val enabled by container.sources.enabled.collectAsStateWithLifecycle()
    val order by container.sources.order.collectAsStateWithLifecycle()
    val instance by container.sources.peerTubeInstance.collectAsStateWithLifecycle()
    var newProfile by remember { mutableStateOf(false) }
    var instanceText by remember(instance) { mutableStateOf(instance) }
    var instanceError by remember { mutableStateOf<String?>(null) }
    var confirmDownloads by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) app.exportProfile(ctx.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.importProfile(ctx.contentResolver, uri)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun setUpdates(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 && !container.notifier.canNotify()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        up.set("creatorUpdates", up.creatorUpdates, on)
        CreatorUpdatesWorker.schedule(ctx, on)
    }

    Column(Modifier.fillMaxSize().padding(pad)) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Section("Profiles") }
            items(profiles, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) },
                    leadingContent = { RadioButton(p.id == current?.id, { app.selectProfile(p.id) }) },
                    trailingContent = { if (profiles.size > 1) IconButton({ app.deleteProfile(p) }) { Icon(Icons.Outlined.Delete, "Delete profile ${p.name}") } },
                    modifier = Modifier.clickable { app.selectProfile(p.id) })
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton({ newProfile = true }) { Text("Add") }
                    TextButton({ exportLauncher.launch("wishtube-${current?.name ?: "profile"}.json") }) { Text("Export") }
                    TextButton({ importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
                }
            }

            item { Section("Appearance") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                        FilterChip(theme == k, { up.setTheme(k) }, { Text(l) })
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) item { SwitchRow("Dynamic colors", "Use your wallpaper colors.", dynamic) { up.set("dynamic", up.dynamicColor, it) } }
            item { SwitchRow("Compact feed", "Smaller thumbnails, more videos per screen.", compact) { up.set("compact", up.compact, it) } }
            item { SwitchRow("Reduce motion", "Fewer transitions and no shimmer animation.", reduce) { up.set("reduceMotion", up.reduceMotion, it) } }
            item { SwitchRow("Glass effects", "Real backdrop blur glass on bars and overlays.", glassEffects) { up.set("glassEffects", up.glassEffects, it) } }
            item { SwitchRow("Show mature content", "Allow adult or age-restricted content in search and feeds.", showMature) { up.setShowMature(it) } }

            item { Section("Playback") }
            item { SwitchRow("Autoplay", "Start playing as soon as a video opens.", autoplay) { up.set("autoplay", up.autoplay, it) } }
            item { SwitchRow("Autoplay next video", "Automatically play the next video from the related list.", autoplayNext) { up.setAutoplayNext(it) } }
            item { SwitchRow("Captions by default", "Show captions when the video has them.", captions) { up.set("captions", up.captions, it) } }
            item { SwitchRow("Background playback", "Keep audio playing when you leave the app.", background) { up.set("background", up.background, it) } }
            item { SwitchRow("Picture-in-picture", "Shrink the video when you leave the app.", pip) { up.set("pip", up.pip, it) } }
            item { SwitchRow("Double-tap to seek", "Double-tap the left or right side of the video to skip 10 seconds.", gestures) { up.set("gestures", up.gestures, it) } }
            item { Label("Default quality") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "1080p", "720p", "480p", "360p").forEachIndexed { i, l -> FilterChip(quality == i, { up.setQuality(i) }, { Text(l) }) }
                }
            }
            item { Label("Default speed") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s -> FilterChip(speed == s, { up.setSpeed(s) }, { Text("${s}x") }) }
                }
            }

            item { Section("Sources") }
            items(order, key = { it.name }) { s ->
                ListItem(headlineContent = { Text(s.label) },
                    supportingContent = { Text("Content stays on ${s.label}; this app is only a client.") },
                    leadingContent = { Switch(s in enabled, { container.sources.setEnabled(s, it) }) },
                    trailingContent = {
                        Row {
                            IconButton({ container.sources.move(s, -1) }) { Icon(Icons.Outlined.ArrowUpward, "Move ${s.label} up") }
                            IconButton({ container.sources.move(s, 1) }) { Icon(Icons.Outlined.ArrowDownward, "Move ${s.label} down") }
                        }
                    })
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(instanceText, { instanceText = it; instanceError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("PeerTube instance for the feed") }, isError = instanceError != null,
                        supportingText = { Text(instanceError ?: "Search covers many instances; the home feed uses this one.") })
                    TextButton({ instanceError = container.sources.setPeerTubeInstance(instanceText) }) { Text("Save instance") }
                    Text("Sign-in to sources is not implemented; watching never requires an account.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item { Section("Recommendations (profile: ${current?.name ?: ""})") }
            items(specs) { s ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(s.label, style = MaterialTheme.typography.titleSmall)
                    Text(s.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(s.get(weights), { app.setWeights(s.set(weights, it)) }, valueRange = 0f..1f,
                        modifier = Modifier.semantics { contentDescription = s.label })
                }
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton({ app.setWeights(RecWeights()) }) { Text("Reset sliders") }
                    TextButton({ app.clearRecommendations() }) { Text("Clear recommendation data") }
                }
            }

            item { Section("Notifications (local only)") }
            item { SwitchRow("Followed creator updates", "Checks a few times a day and notifies on this device. No server involved.", updates) { setUpdates(it) } }

            item { Section("Privacy & data") }
            item { Text("Everything is stored on this device. No account, no analytics, no tracking.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    TextButton({ app.clearHistory() }) { Text("Clear history for this profile") }
                    TextButton({ app.clearSearches() }) { Text("Clear recent searches") }
                    TextButton({ app.clearCache() }) { Text("Clear cached video metadata") }
                    TextButton({ confirmDownloads = true }) { Text("Delete all downloads") }
                }
            }
        }
    }
    if (newProfile) NameDialog("New profile", { newProfile = false }) { app.createProfile(it); newProfile = false }
    if (confirmDownloads) AlertDialog(onDismissRequest = { confirmDownloads = false }, title = { Text("Delete all downloads?") },
        text = { Text("Downloaded files will be removed from this device.") },
        confirmButton = { TextButton({ app.deleteAllDownloads(); confirmDownloads = false }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDownloads = false }) { Text("Cancel") } })
}

@Composable
private fun Section(title: String) =
    Text(title, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Label(text: String) =
    Text(text, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)

/** Whole row is the toggle target (large touch target, correct screen-reader role). */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange))
}
