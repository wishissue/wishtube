# WishTube

WishTube is a local-first, privacy-focused Android universal media client that unifies decentralized and open media sources—**PeerTube**, **Odysee**, **Internet Archive**, **media.ccc.de**, and **Wikimedia Commons**—into a single, polished application.

No account required, no tracking, no cloud servers. Profiles, watch history, saved items, playlists, private notes, offline downloads, and local recommendation signals remain 100% on device.

---

## Features

- **Unified Multi-Source Discovery & Search**: Concurrent federated search across PeerTube, Odysee, Internet Archive, media.ccc.de, and Wikimedia Commons with instant progressive result loading and deduplication.
- **Local-First Recommendation Engine**: Ranks content locally based on selected onboarding interests, watch duration signals, search history, and freshness.
- **Content Safety (Mature Content Toggle)**: Optional mature content filter (off by default) to filter adult or age-restricted material across supported sources.
- **License & Rights Management**: Classifies content by license (Public Domain, CC0, CC BY, CC BY-SA) with informational license badges and Restricted-download confirmation dialogs.
- **Robust Media3 Playback**: Powered by AndroidX Media3 (ExoPlayer + OkHttp DataSource) with adaptive HLS/progressive streams, background playback, PiP, subtitle support, and chapter navigation.
- **Offline Downloads & Local Library**: Store videos for offline playback, manage local watch history, playlists, private notes, and JSON profile export/import.
- **Source & Instance Management**: Enable/disable sources, reorder feed sources, and connect to custom PeerTube instances with secure URL validation.

---

## Supported Sources

1. **PeerTube**: Multi-instance parallel feed aggregation and SepiaSearch.
2. **Odysee**: JSON-RPC claim search and streaming URL resolution.
3. **Internet Archive**: Cached and paginated video archives.
4. **media.ccc.de**: Conference recordings and lectures.
5. **Wikimedia Commons**: Educational and media repository videos.

---

## Privacy

WishTube is strictly local-first:
- **No telemetry or analytics**: Zero tracking scripts or third-party SDKs.
- **Local database**: All history, preferences, profiles, and downloads are stored securely in a local Room database (`wishtube.db`).
- **Direct connections**: Network requests communicate directly with official source APIs using a descriptive User-Agent (`WishTube/<version>`).

---

## Architecture

WishTube follows clean MVVM architecture with Jetpack Compose:
- **Package Path**: `app.wishtube`
- **UI Framework**: Jetpack Compose & Material 3 (`androidx.compose`)
- **Playback**: AndroidX Media3 (`androidx.media3`)
- **Persistence**: Room 2.6.1 (`app.wishtube.data`)
- **Network & Serialization**: OkHttp 4.12.0 & `kotlinx.serialization` (`app.wishtube.sources`)

---

## Screenshots

<!-- TODO_SCREENSHOTS_PLACEHOLDER: Add screenshots in docs/screenshots/ -->
*Screenshots coming soon in `docs/screenshots/`*

---

## Installation

### From Releases
Download the signed APK for your device architecture (`arm64-v8a`, `armeabi-v7a`, `x86_64`, or `universal`) from the [GitHub Releases](https://github.com/wishissue/wishtube/releases) page.

### Build from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/wishissue/wishtube.git
   cd wishtube
   ```
2. Open in Android Studio Ladybug+ (JDK 17 required).
3. Build and test via Gradle:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

---

## Testing

Run unit tests locally:
```bash
./gradlew testDebugUnitTest
```

---

## Roadmap

- Additional federated media providers
- Cast / DLNA casting support
- Advanced subtitle styling options

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull requests.

---

## License & Disclaimer

WishTube is licensed under the Apache License, Version 2.0. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for full attribution and notices.

*Disclaimer: WishTube is an independent open-source media client and is not affiliated with, endorsed by, or sponsored by any of the third-party platforms or content providers it connects to.*
