# OpenVideo Manual QA Checklist

## 1. Home & Feeds
- [ ] For You feed loads successfully, respecting recommendation preferences and sliders.
- [ ] New and Chronological feed modes switch correctly and update video lists.
- [ ] Skeleton shimmer displays during initial loading.
- [ ] Pull-to-refresh works on feed screens.

## 2. Discover & Following
- [ ] Discover tab shows curated discovery items, small creators, and random selections.
- [ ] Following tab lists videos from followed creators across enabled sources.
- [ ] Follow/unfollow actions update state immediately and persist across app restarts.

## 3. Search
- [ ] Unified search queries return combined/interleaved results from enabled sources.
- [ ] Filters (Source, Sort by Relevance/Newest/Oldest/Popular, Duration) apply correctly.
- [ ] Recent searches are saved and displayed as suggestions, feeding recommendations.

## 4. Watch Page & Player (Media3)
- [ ] Video playback starts successfully (adaptive HLS / progressive MP4).
- [ ] Play, pause, double-tap seek (±10s) work smoothly.
- [ ] Fullscreen toggle rotates to landscape and expands player.
- [ ] Picture-in-Picture (PiP) triggers automatically on leaving the app while playing (if enabled).
- [ ] Mini-player persists when navigating away from the watch page.
- [ ] Background playback continues when app is backgrounded (with media notification controls).
- [ ] Player menus (Quality, Speed, Captions, Chapters) function correctly.

## 5. Library Tabs
- [ ] History tracks watched videos and supports resume position.
- [ ] Watch Later, Saved, Liked bookmarks save and filter correctly.
- [ ] Playlists creation, deletion, and adding/removing videos work.
- [ ] Downloads tab shows progress, status (Running/Done/Failed), and plays offline media.
- [ ] Private notes can be added, edited, and viewed per video.
- [ ] Multiple profiles can be created, switched between, and exported/imported via JSON.

## 6. Settings & Customization
- [ ] Source toggles (PeerTube, Odysee) enable/disable sources and update feeds/search.
- [ ] Custom PeerTube instance setting validates input and loads feed from the specified instance.
- [ ] Theme settings (Light, Dark, System, Dynamic Colors) apply instantly.
- [ ] Playback defaults (quality, speed, captions, autoplay) apply to new player sessions.

## 7. Creator Pages
- [ ] Creator header shows avatar, name, description, follower count, and follow button.
- [ ] Creator video list paginates correctly.

## 8. Deep Links
- [ ] Opening `odysee.com/...` or `lbry://...` deep link launches OpenVideo and resolves the video.
- [ ] Opening PeerTube `/w/...` or `/videos/watch/...` deep link launches OpenVideo and resolves the video.
