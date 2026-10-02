# WishTube

A local-first, privacy-focused Android universal media client that unifies PeerTube, Odysee, Internet Archive, media.ccc.de, and Wikimedia Commons into a cozy, warm, liquid glass interface.

No account required, no tracking, no cloud servers. Profiles, watch history, saved items, playlists, private notes, offline downloads, and local recommendation signals remain 100% on device.

## Open in Android Studio
1. File > Open > select this folder (Android Studio Ladybug or newer, JDK 17).
2. Let Gradle sync (Gradle 8.9 wrapper included).
3. Run the `app` configuration on a device/emulator (Android 8.0+ / API 26+).

## Features
- **Unified Discovery & Search**: Concurrent search across PeerTube, Odysee, Internet Archive, media.ccc.de, and Wikimedia Commons with provider badges and instant progressive result loading.
- **License-Aware Rights Model**: Classifies content by license (Public Domain, CC0, CC BY, CC BY-SA) and reuse status (Remixable, Check Rights, Restricted) with visual license badges.
- **Proven Open-Source Playback Engine**: Powered by AndroidX Media3 (ExoPlayer, OkHttp DataSource, HLS, DASH) with automatic candidate stream fallbacks, buffering indicators, subtitle tracks, Picture-in-Picture, and background playback.
- **Cozy Liquid Glass Aesthetic**: Custom `GlassSurface` components featuring frosted gradients, refraction borders, soft shadows, rounded 28dp shape tokens, and a warm palette (Cream, Terracotta, Sage, Coffee Brown).
- **Local User Library & Playlists**: Local Room database storing Watch Later, Saved, Liked, private Notes, custom multi-source Playlists, and JSON profile export/import.
- **Source Management**: Reorder, enable/disable sources, and add custom PeerTube instances in Settings.

## Architecture & Technology
- **UI Framework**: Jetpack Compose (Compose BOM 2024.12.01, Material 3, Material Symbols Rounded).
- **Playback**: AndroidX Media3 (ExoPlayer 1.5.1 + OkHttp DataSource 1.5.1).
- **Database**: Room 2.6.1 with destructive migration fallback.
- **Networking**: OkHttp 4.12.0 + kotlinx.serialization.
- **Image Caching**: Coil 2.7.0 with OkHttp call factory.

## Licensing & Disclaimer
WishTube is licensed under the Apache License, Version 2.0. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for full license notices.

*Disclaimer: WishTube is an independent open-source media client and is not affiliated with, endorsed by, or sponsored by any of the third-party platforms or content providers it connects to.*
