<p align="center">
  <img src="images/icon.png" alt="WishTube icon" width="180">
</p>

<h1 align="center">WishTube</h1>

<p align="center">
  <b>One app for open video. No account, no tracking, no cloud.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Material-3-E8704F?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3">
  <img src="https://img.shields.io/badge/data-100%25%20on%20device-brightgreen?style=for-the-badge" alt="Local first">
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge" alt="License">
</p>

<p align="center">
  <a href="#screenshots">Screenshots</a> ·
  <a href="#features">Features</a> ·
  <a href="#supported-sources">Sources</a> ·
  <a href="#privacy">Privacy</a> ·
  <a href="#build-it">Build</a> ·
  <a href="#license-and-disclaimer">License</a>
</p>

---

> **WishTube** is a local-first Android media client. It pulls **PeerTube, Odysee, Internet Archive, media.ccc.de and Wikimedia Commons** into one cozy, warm, liquid-glass interface. Search everything at once, watch it, save it. Your profile, history, playlists and notes never leave your phone.

---

## Screenshots

<table align="center">
  <tr>
    <td align="center"><img src="images/home.png" alt="Home" width="100%"></td>
    <td align="center"><img src="images/serch.png" alt="Search" width="100%"></td>
    <td align="center"><img src="images/video.png" alt="Video page" width="100%"></td>
    <td align="center"><img src="images/videoplayer.png" alt="Player" width="100%"></td>
    <td align="center"><img src="images/resources.png" alt="Sources settings" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><b>Home</b></td>
    <td align="center"><b>Search</b></td>
    <td align="center"><b>Video</b></td>
    <td align="center"><b>Player</b></td>
    <td align="center"><b>Sources</b></td>
  </tr>
</table>

---

## Features

| Feature | What you get |
| --- | --- |
| **Unified search** | One query goes to every enabled source in parallel. Results appear progressively, each with a provider badge. |
| **License-aware** | Videos are tagged by license (Public Domain, CC0, CC BY, CC BY-SA) and reuse status (Remixable, Check Rights, Restricted). |
| **Solid playback** | AndroidX Media3 (ExoPlayer) with HLS and DASH, automatic stream fallback, subtitles, Picture-in-Picture and background playback. |
| **Local library** | Watch Later, Saved, Liked, private notes and multi-source playlists. Export or import your profile as JSON. |
| **Your sources** | Reorder them, switch them on or off, or add your own PeerTube instances in Settings. |
| **Liquid glass UI** | Frosted gradients, refraction borders, soft shadows and a warm palette of cream, terracotta, sage and coffee brown. |

---

## Supported sources

| Source | What WishTube uses it for |
| --- | --- |
| **PeerTube** | Parallel feeds from multiple instances, plus SepiaSearch |
| **Odysee** | JSON-RPC claim search and stream URL resolution |
| **Internet Archive** | Cached, paginated video archives |
| **media.ccc.de** | Conference recordings and lectures |
| **Wikimedia Commons** | Educational and media-repository videos |

---

## Privacy

- No account, no sign-in
- No analytics, no tracking
- No WishTube server. Everything is stored on your device
- The app only talks to the video sources you have enabled, to search and stream

---

## Build it

1. Open this folder in **Android Studio** (Ladybug or newer, JDK 17).
2. Let Gradle sync (the Gradle 8.9 wrapper is included).
3. Run the `app` configuration on a device or emulator (**Android 8.0+ / API 26+**).

Or from the command line:

```bash
./gradlew assembleDebug
```

<details>
<summary><b>Built with</b></summary>

<br>

| Part | Tech |
| --- | --- |
| UI | Jetpack Compose, Material 3, Material Symbols Rounded |
| Playback | AndroidX Media3 (ExoPlayer + OkHttp DataSource) |
| Database | Room |
| Networking | OkHttp + kotlinx.serialization |
| Images | Coil |

</details>

---

## Contributing

Found a bug or want a source added? Open an issue at [github.com/wishissue/wishtube/issues](https://github.com/wishissue/wishtube/issues).

---

## License and disclaimer

WishTube is released under the **Apache License 2.0**. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for third-party notices and [OPEN_SOURCE_SOURCES.md](OPEN_SOURCE_SOURCES.md) for the sources list.

> *WishTube is an independent open-source media client. It is not affiliated with, endorsed by, or sponsored by any of the platforms or content providers it connects to.*
