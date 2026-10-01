# Third-party licenses

| Component | Version | License | Use |
|---|---|---|---|
| AndroidX Compose (UI, Material3, icons-extended) | BOM 2024.12.01 | Apache-2.0 | UI, icons |
| AndroidX Activity/Core/Lifecycle/Navigation | see app/build.gradle.kts | Apache-2.0 | App framework |
| AndroidX Room | 2.6.1 | Apache-2.0 | Local database |
| AndroidX Media3 (ExoPlayer, HLS, UI) | 1.5.1 | Apache-2.0 | Playback, player UI, speed/track menus |
| Coil | 2.7.0 | Apache-2.0 | Image loading and caching |
| AndroidX Media3 Session | 1.5.1 | Apache-2.0 | Background playback service, notification controls |
| AndroidX WorkManager | 2.9.1 | Apache-2.0 | Optional followed-creator update checks |
| Guava (Android flavor) | 33.3.1-android | Apache-2.0 | Media3 session controller futures |
| OkHttp | 4.12.0 | Apache-2.0 | Networking |
| kotlinx.serialization / coroutines, Kotlin stdlib | 1.7.3 / 1.9.0 / 2.0.21 | Apache-2.0 | JSON, concurrency |

External services (HTTP APIs only, no code copied): PeerTube REST API, SepiaSearch, Odysee/LBRY SDK proxy.
Transitive dependencies are NOT yet audited. Run a license report plugin and review manually before release.
No fonts, images, sounds or Lottie files are bundled (system font; original vector icon).
