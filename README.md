# OpenVideo

Local-first, privacy-focused Android video client that unifies PeerTube, Odysee, Internet Archive, media.ccc.de, and Wikimedia Commons in one interface.
No account, no backend, no analytics. Profiles, history, playlists, notes, downloads and recommendations all live on the device.

## Open in Android Studio
1. File > Open > select this folder (Android Studio Ladybug or newer, JDK 17).
2. Let Gradle sync (the Gradle 8.9 wrapper is included). Accept any suggested dependency bumps.
3. Run the `app` configuration on a device/emulator (API 26+).
If you ran an earlier zip of this project, uninstall it first (the database schema changed).

## Implemented
- Sources: PeerTube (multi-instance parallel aggregation + SepiaSearch), Odysee/LBRY (free streams), Internet Archive, media.ccc.de, and Wikimedia Commons. Adapter architecture with per-source capabilities; one source failing never breaks the others; source reorder/enable/instance settings.
- Robust Playback (Media3 + OkHttp DataSource): shared OkHttp client with proper User-Agent, redirects enabled, and automatic ordered candidate fallback (HLS -> best MP4 -> lower res MP4s) with detailed error reporting.
- Glass UI & Polish: Real backdrop blur glass using Haze library for bottom navigation bar, mini-player, top bars, search bar, and overlays. Settings toggle "Glass effects" with automatic fallbacks on Android < 12, battery saver, reduce motion, and WCAG AA contrast.
- Discovery: Home (For You / New / Chronological), Discover, Following, unified search, creator pages, deep links.
- Local recommendations & Language Preference: topic/creator/recency/popularity/language signals, preferred languages setting, local heuristic language detector.
- Library: History with resume, Watch Later, Saved, Liked, Playlists, Downloads (offline playback), private Notes, multiple profiles, JSON export/import of a profile.

## Deliberately NOT implemented (would be fake or inapplicable)
- SponsorBlock / DeArrow: keyed by YouTube video IDs which these platforms don't expose.
- Source authentication: watching never needs an account.

## Known limitations / verify before release
- Downloads run inside the app process; if Android kills the app mid-download it is marked failed and can be retried.
- Run a real dependency license audit (see THIRD_PARTY_LICENSES.md). Check accessibility with TalkBack and font scaling on device.
