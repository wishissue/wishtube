# Contributing to WishTube

Thank you for your interest in contributing to WishTube!

## Development Setup

1. **Prerequisites**: Android Studio Ladybug or newer, JDK 17, Android SDK (API 35).
2. **Clone & Open**: Open the project root folder in Android Studio.
3. **Build & Test**:
   - Run unit tests: `./gradlew testDebugUnitTest`
   - Build debug APK: `./gradlew assembleDebug`
   - Run lint: `./gradlew lintDebug`

## Code Style

- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep composables clean, modular, and performant.
- Use AndroidX Media3 for media playback and Room for local persistence.

## Adding a New Video Source

To integrate a new media source:
1. Implement the `VideoSource` interface (`app.wishtube.sources.VideoSource`).
2. Register the new adapter in `SourceManager` (`app.wishtube.sources.SourceManager`).
3. Add comprehensive unit tests in `app/src/test/java/app/wishtube/sources/`.

## Commit Message Style

Follow conventional commits format where possible:
- `feat: add ...`
- `fix: resolve ...`
- `refactor: simplify ...`
- `docs: update ...`
