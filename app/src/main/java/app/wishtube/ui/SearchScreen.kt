package app.wishtube.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.wishtube.App
import app.wishtube.domain.*

enum class DurationFilter(val label: String, val ok: (Long) -> Boolean) {
    ANY("Any length", { true }), SHORT("Under 4 min", { it < 240 }),
    MEDIUM("4–20 min", { it in 240L..1200L }), LONG("Over 20 min", { it > 1200 })
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as App).container
    var query by mutableStateOf("")
    var sort by mutableStateOf(SearchSort.RELEVANCE)
    var sources by mutableStateOf(SourceId.entries.toSet())
    var duration by mutableStateOf(DurationFilter.ANY)
    var loading by mutableStateOf(false)
    var results by mutableStateOf<List<Video>?>(null)
    var errors by mutableStateOf<Map<SourceId, String>>(emptyMap())
    private var job: Job? = null

    fun run() {
        val q = query.trim(); if (q.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            val r = c.sources.query(sources) { it.search(q, 0, sort) }
            val validVideos = r.videos.filter { it.title.isNotBlank() && it.sourceVideoId.isNotBlank() }
            results = when (sort) {   // each source ranks differently, so re-apply explicit sorts locally
                SearchSort.NEWEST -> validVideos.sortedByDescending { it.uploadDate }
                SearchSort.OLDEST -> validVideos.sortedBy { it.uploadDate }
                SearchSort.POPULAR -> validVideos.sortedByDescending { it.viewCount ?: 0L }
                SearchSort.RELEVANCE -> validVideos
            }
            errors = r.errors; loading = false
        }
    }
}

@Composable
fun SearchScreen(app: AppViewModel, pad: PaddingValues, nav: Nav, onBack: () -> Unit) {
    val vm: SearchViewModel = viewModel()
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val recent by app.recentSearches.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.results == null) requester.requestFocus() }
    val shown = remember(vm.results, vm.duration) { vm.results?.filter { vm.duration.ok(it.durationSec) } }
    val cells = feedCells()

    fun submit() { vm.run(); app.recordSearch(vm.query); focus.clearFocus() }

    Column(Modifier.fillMaxSize().padding(pad)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                placeholder = { Text("Search videos") }, shape = RoundedCornerShape(28.dp),
                trailingIcon = { if (vm.query.isNotEmpty()) IconButton({ vm.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(requester))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchSort.entries) { s -> FilterChip(vm.sort == s, { vm.sort = s; vm.run() }, { Text(s.label) }) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SourceId.entries) { s ->
                FilterChip(s in vm.sources, {
                    val n = if (s in vm.sources) vm.sources - s else vm.sources + s
                    if (n.isNotEmpty()) { vm.sources = n; vm.run() }
                }, { Text(s.label) })
            }
            items(DurationFilter.entries) { d -> FilterChip(vm.duration == d, { vm.duration = d }, { Text(d.label) }) }
        }
        if (vm.errors.isNotEmpty() && !shown.isNullOrEmpty()) ErrorBanner(vm.errors) { vm.run() }
        Box(Modifier.weight(1f)) {
            when {
                vm.loading -> LazyVerticalGrid(cells, contentPadding = feedPadding(),
                    horizontalArrangement = Arrangement.spacedBy(feedHGap()), verticalArrangement = Arrangement.spacedBy(feedSpacing())) { items(4) { SkeletonCard() } }
                shown == null && recent.isNotEmpty() -> LazyColumn {
                    item { Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton({ app.clearSearches() }) { Text("Clear") }
                    } }
                    items(recent, key = { it.term }) { r ->
                        ListItem(headlineContent = { Text(r.term) }, leadingContent = { Icon(Icons.Outlined.History, null) },
                            modifier = Modifier.clickable { vm.query = r.term; submit() })
                    }
                }
                shown == null -> EmptyState(Icons.Outlined.Search, "Search across sources", "Results from PeerTube, Odysee, Internet Archive, media.ccc.de, and Wikimedia Commons appear together.")
                shown.isEmpty() && vm.errors.isNotEmpty() -> FailureState(vm.errors, nav) { vm.run() }
                shown.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "Nothing found", "Try another search or source.")
                else -> LazyVerticalGrid(cells, contentPadding = feedPadding(),
                    horizontalArrangement = Arrangement.spacedBy(feedHGap()), verticalArrangement = Arrangement.spacedBy(feedSpacing())) {
                    items(shown, key = { it.id }, contentType = { "video_card" }) { v -> VideoCard(v, app, { nav.openVideo(v) }, Modifier.animateItem()) }
                }
            }
        }
    }
}
