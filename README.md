# OpenVideo

Local-first, privacy-focused Android video client that unifies PeerTube and Odysee in one interface.
No account, no backend, no analytics. Profiles, history, playlists, notes, downloads and recommendations all live on the device.

## Open in Android Studio
1. File > Open > select this folder (Android Studio Ladybug or newer, JDK 17).
2. Let Gradle sync (the Gradle 8.9 wrapper is included). Accept any suggested dependency bumps.
3. Run the `app` configuration on a device/emulator (API 26+).
If you ran an earlier zip of this project, uninstall it first (the database schema changed, version is still 1 pre-release).

## Implemented
- Sources: PeerTube (SepiaSearch + your chosen instance; captions, chapters, full description, downloads) and Odysee/LBRY (free streams only).
  Adapter architecture with per-source capabilities; one source failing never breaks the others; source reorder/enable/instance settings.
- Discovery: Home (For You / New / Chronological), Discover (Discover / Small creators / Random), Following, unified search
  (sort, source, duration filters, recent searches that also feed recommendations), creator pages, deep links
  (odysee.com, lbry://, PeerTube /w/ and /videos/watch/ links).
- Local recommendations: topic/creator/recency/popularity/small-creator/randomness/length signals, adjustable per-profile sliders with
  plain-language help, "why this appeared" reasons, Not Interested, hide creator/topic, clear recommendation data.
- Library: History with resume, Watch Later, Saved, Liked, Playlists, Downloads (offline playback), private Notes, multiple profiles,
  JSON export/import of a profile.
- Player (Media3): fullscreen, landscape, double-tap seek (toggle), PiP, mini-player, captions/audio/quality/speed menus, chapters,
  default quality/speed/captions/autoplay settings, background playback with notification controls (toggle).
- Polish: skeleton shimmer, image caching + prefetch, light/dark/system/dynamic colors, compact feed density, reduce motion,
  phone/tablet adaptive layouts, offline/failure/empty states, haptics, accessibility semantics (headings, labels, large touch targets).
- Notifications (all local, optional): download results and followed-creator updates (WorkManager, every ~6h).

## Deliberately NOT implemented (would be fake or inapplicable)
- SponsorBlock / DeArrow: both are keyed by YouTube video IDs, which PeerTube and Odysee videos don't expose. Revisit if a YouTube-ID source is ever added.
- Source authentication (sign-in, source-native subscribe/comment/like): not built; watching never needs an account.
- Glass/blur effects: real backdrop blur is costly and the current layouts have nothing scrolling behind bars, so it was left out per "performance first".
- Blur-to-sharp image transition: thumbnails crossfade instead.

## Known limitations / verify before release
- Written and reviewed without a full Android build environment (only a syntax-level compiler pass). Expect minor compile fixes on first sync.
- Odysee API endpoint and stream URL scheme are third-party; PeerTube feed depends on the chosen instance being up.
- Downloads run inside the app process; if Android kills the app mid-download it is marked failed and can be retried.
- Remove nothing silently: if you change database entities later, add a Room migration (no destructive fallback is configured).
- Run a real dependency license audit (see THIRD_PARTY_LICENSES.md). Check accessibility with TalkBack and font scaling on device.
