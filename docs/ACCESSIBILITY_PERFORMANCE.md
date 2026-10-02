# Accessibility and Performance Checklist

This document outlines accessibility design intentions, performance targets, and verification checklist items for WishTube.

## 1. Accessibility (TalkBack & Navigation)
- [ ] **Touch Targets**: Interactive elements (buttons, filter chips, list items, player controls) should meet or exceed recommended minimum touch target dimensions.
- [ ] **Semantics & Headings**: Screen titles and section headers are annotated with `Modifier.semantics { heading() }` for TalkBack heading navigation.
- [ ] **Content Descriptions**: Image views, video thumbnails, and icon buttons include descriptive `contentDescription` attributes.
- [ ] **Font Scaling**: Layouts utilize scalable typography (`MaterialTheme.typography`) with flexible sizing and wrapping to support accessibility font scaling.

## 2. Performance & Threading Intentions
- [ ] **Main Thread Isolation**:
  - Room database queries execute via `suspend` functions or Flows on `Dispatchers.IO`.
  - Network requests (`OkHttp` calls in adapters) execute inside `withContext(Dispatchers.IO)`.
  - Zero database or network operations occur on the main thread.
- [ ] **Recomposition Optimization**:
  - Stable parameters and state collection (`collectAsStateWithLifecycle`) are used throughout Compose UI hierarchies.
  - Image loading uses Coil with disk/memory caching.
- [ ] **Verification Checklist**:
  - [ ] Profile cold startup time on physical test devices.
  - [ ] Feed scrolling framerate (FPS) stability during rapid video grid scrolling.
  - [ ] StrictMode I/O violation audit on physical device debug builds.
