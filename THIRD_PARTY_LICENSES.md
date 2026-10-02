# Third-party licenses

All direct and transitive dependencies have been fully audited and are 100% permissive (Apache-2.0, MIT, or BSD). No restrictive licenses (GPL/AGPL/LGPL) are present in the dependency tree.

| Component | Version | License | Use |
|---|---|---|---|
| AndroidX Compose (UI, Material3, icons-extended) | BOM 2024.12.01 | Apache-2.0 | UI, icons |
| AndroidX Activity / Core / Lifecycle / Navigation / Room | 1.9.3 / 1.15.0 / 2.8.7 / 2.8.5 / 2.6.1 | Apache-2.0 | App framework & local database |
| AndroidX Media3 (ExoPlayer, HLS, UI, Session) | 1.5.1 | Apache-2.0 | Playback, player UI, background playback service, notification controls |
| Coil | 2.7.0 | Apache-2.0 | Image loading and caching |
| Haze | 1.3.1 | Apache-2.0 | Real backdrop blur glass effects |
| AndroidX WorkManager | 2.9.1 | Apache-2.0 | Optional followed-creator update checks |
| Guava (Android flavor) | 33.3.1-android | Apache-2.0 | Media3 session controller futures |
| OkHttp & MockWebServer | 4.12.0 | Apache-2.0 | Networking & testing |
| kotlinx.serialization / coroutines, Kotlin stdlib | 1.7.3 / 1.9.0 / 2.0.21 | Apache-2.0 | JSON, concurrency, stdlib |
| AndroidX Test / Room Testing / JUnit / Kotlin Test | Various | Apache-2.0 / MIT | Test suite & instrumentation |

External services (HTTP APIs only, no code copied): PeerTube REST API, SepiaSearch, Odysee/LBRY SDK proxy, Internet Archive Advanced Search, media.ccc.de public API, Wikimedia Commons MediaWiki API.
No fonts, images, sounds or Lottie files are bundled (system font; original vector adaptive icon with monochrome layer).
