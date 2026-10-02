# WishTube Manual QA Checklist

## 1. Home & Feeds
- [ ] For You feed loads successfully, respecting recommendation preferences and sliders.
- [ ] New and Chronological feed modes switch correctly and update video lists.
- [ ] Skeleton shimmer displays during initial loading.
- [ ] Pull-to-refresh works on feed screens.

## 2. Discover & Following
- [ ] Discover tab shows curated discovery items, small creators, and random selections.
- [ ] Following tab lists videos from followed creators across enabled sources.
- [ ] Follow/unfollow actions update state immediately and persist across app restarts.

## 3. Search & Multi-Source Verification
- [ ] Unified search queries return combined/interleaved results from enabled sources.
- [ ] **PeerTube**: Multi-instance parallel search and feed aggregation functions correctly.
- [ ] **Odysee**: JSON-RPC search and streaming resolution return playable media.
- [ ] **Internet Archive**: Cached and paginated video archives load successfully.
- [ ] **media.ccc.de**: Conference recordings and lectures load successfully.
- [ ] **Wikimedia Commons**: Educational and media repository videos load successfully.
- [ ] Filters (Source, Sort by Relevance/Newest/Oldest/Popular, Duration) apply correctly.
- [ ] Recent searches are saved and displayed as suggestions, feeding recommendations.

## 4. Content Safety & Rights
- [ ] **Show mature content toggle**: When disabled (default), adult or age-restricted content is filtered out of feeds and search results. When enabled, mature content is displayed.
- [ ] **Restricted-download dialog**: Attempting to download a video marked as `Restricted` or `Check Rights` triggers an informational confirmation dialog explaining the license before downloading.
- [ ] License badges and source links display correctly on the watch page.

## 5. Watch Page & Player (Media3)
- [ ] Video playback starts successfully (adaptive HLS / progressive MP4).
- [ ] Play, pause, double-tap seek (±10s) work smoothly.
- [ ] Fullscreen toggle rotates to landscape and expands player.
- [ ] Picture-in-Picture (PiP) triggers automatically on leaving the app while playing (if enabled).
- [ ] Mini-player persists when navigating away from the watch page.
- [ ] Background playback continues when app is backgrounded (with media notification controls).
- [ ] Player menus (Quality, Speed, Captions, Chapters) function correctly.

## 6. Library Tabs
- [ ] History tracks watched videos and supports resume position.
- [ ] Watch Later, Saved, Liked bookmarks save and filter correctly.
- [ ] Playlists creation, deletion, and adding/removing videos work.
- [ ] Downloads tab shows progress, status (Running/Done/Failed), and plays offline media.
- [ ] Private notes can be added, edited, and viewed per video.
- [ ] Multiple profiles can be created, switched between, and exported/imported via JSON.

## 7. Settings & Customization
- [ ] Source toggles enable/disable sources and update feeds/search.
- [ ] Custom PeerTube instance setting validates input and loads feed from the specified instance.
- [ ] Theme settings (Light, Dark, System, Dynamic Colors) apply instantly.
- [ ] Playback defaults (quality, speed, captions, autoplay) apply to new player sessions.

## 8. Creator Pages
- [ ] Creator header shows avatar, name, description, follower count, and follow button.
- [ ] Creator video list paginates correctly.

## 9. Deep Links
- [ ] Opening `odysee.com/...` or `lbry://...` deep link launches WishTube and resolves the video.
- [ ] Opening PeerTube `/w/...` or `/videos/watch/...` deep link launches WishTube and resolves the video.
