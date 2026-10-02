package org.openvideo.aggregator.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.imageLoader
import coil.request.ImageRequest
import org.openvideo.aggregator.App
import org.openvideo.aggregator.data.toVideo
import org.openvideo.aggregator.domain.*
import org.openvideo.aggregator.recommend.RecWeights
import org.openvideo.aggregator.recommend.Ranked
import org.openvideo.aggregator.recommend.RecommendationEngine
import org.openvideo.aggregator.sources.SourceManager

data class FeedState(
    val loading: Boolean = true, val items: List<Ranked> = emptyList(),
    val errors: Map<SourceId, String> = emptyMap(),
)

class FeedViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as App).container
    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()
    private var raw: List<Video> = emptyList()
    private var job: Job? = null

    /** Local-first: show cached videos immediately, then refresh from the network. */
    fun refresh(mode: FeedMode, profileId: Long, weights: RecWeights) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            if (raw.isEmpty()) {
                val cached = c.dao.recentCached(80).map { it.toVideo() }
                if (cached.isNotEmpty()) { raw = cached; rank(mode, profileId, weights, loading = true, errors = null) }
            }
            val res: SourceManager.Result = if (mode == FeedMode.FOLLOWING) {
                val pairs = c.dao.followsNow(profileId).take(25).map { SourceId.valueOf(it.source) to it.creatorId }
                c.sources.creators(pairs)
            } else {
                val sort = if (mode == FeedMode.NEW || mode == FeedMode.CHRONOLOGICAL) FeedSort.NEWEST else FeedSort.TRENDING
                c.sources.query { s -> (0..1).flatMap { s.feed(it, sort) } }
            }
            if (res.videos.isNotEmpty()) { c.library.cache(res.videos); raw = res.videos }
            rank(mode, profileId, weights, loading = false, errors = res.errors)
        }
    }

    fun reRank(mode: FeedMode, profileId: Long, weights: RecWeights) {
        viewModelScope.launch { if (raw.isNotEmpty()) rank(mode, profileId, weights, _state.value.loading, null) }
    }

    private suspend fun rank(mode: FeedMode, pid: Long, w: RecWeights, loading: Boolean, errors: Map<SourceId, String>?) {
        val interests = c.userPrefs.selectedInterests.value
        val sig = c.library.signals(pid, interests)
        val ranked = withContext(Dispatchers.Default) { RecommendationEngine.rank(raw, sig, w, mode) }
        _state.update { it.copy(items = ranked, loading = loading, errors = errors ?: it.errors) }
        // Prefetch the first thumbnails so scrolling feels instant.
        val ctx = getApplication<Application>()
        ranked.take(12).forEach { r ->
            r.video.thumbnail?.let { ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(it).size(640, 360).build()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(title: String, modes: List<FeedMode>, vmKey: String, app: AppViewModel, pad: PaddingValues, nav: Nav) {
    val feedVm: FeedViewModel = viewModel(key = vmKey)
    val state by feedVm.state.collectAsStateWithLifecycle()
    val profile by app.profile.collectAsStateWithLifecycle()
    val weights by app.weights.collectAsStateWithLifecycle()
    val dismissed by app.dismissed.collectAsStateWithLifecycle()
    val hidden by app.hiddenCreators.collectAsStateWithLifecycle()
    val follows by app.follows.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(modes.first()) }
    val pid = profile?.id
    val followKey = if (mode == FeedMode.FOLLOWING) follows.size else 0

    LaunchedEffect(mode, pid, followKey) { if (pid != null) feedVm.refresh(mode, pid, weights) }
    LaunchedEffect(weights) { if (pid != null) feedVm.reRank(mode, pid, weights) }

    val visible = state.items.filter { it.video.id !in dismissed && it.video.creatorKey !in hidden }
    val retry: () -> Unit = { if (pid != null) feedVm.refresh(mode, pid, weights) }
    val cells = feedCells()
    val gap = feedSpacing()

    Column(Modifier.fillMaxSize().padding(pad)) {
        ScreenTopBar(title, nav)
        if (modes.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modes) { m -> FilterChip(selected = m == mode, onClick = { mode = m }, label = { Text(m.label) }) }
        }
        if (state.errors.isNotEmpty() && visible.isNotEmpty()) ErrorBanner(state.errors) { retry() }

        PullToRefreshBox(isRefreshing = state.loading && visible.isNotEmpty(), onRefresh = retry, modifier = Modifier.weight(1f)) {
            when {
                mode == FeedMode.FOLLOWING && follows.isEmpty() -> EmptyState(Icons.Outlined.Subscriptions, "Not following anyone yet",
                    "Follow creators from a video or creator page and their latest videos will appear here.")
                visible.isEmpty() && state.loading -> LazyVerticalGrid(cells, contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(6) { SkeletonCard() }
                }
                visible.isEmpty() && state.errors.isNotEmpty() -> FailureState(state.errors, nav, retry)
                visible.isEmpty() -> EmptyState(Icons.Outlined.VideoLibrary, "Nothing here yet", "Pull down to refresh or try another mode.")
                else -> LazyVerticalGrid(cells, contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                    items(visible, key = { it.video.id }) { r ->
                        VideoCard(r.video, app, { nav.openVideo(r.video) }, Modifier.animateItem(),
                            reason = if (mode == FeedMode.FOR_YOU || mode == FeedMode.DISCOVER || mode == FeedMode.SMALL) r.reasons.firstOrNull() else null)
                    }
                }
            }
        }
    }
}
