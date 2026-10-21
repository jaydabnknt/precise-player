# Precise Player — working notes (read first)

Rebuild of the old "Precise Frame Seek Volume mpv Video Player" (2022 ad-laden fork of mpv-android)
as a modern, ad-free, Play-Store-ready app. Owner builds only through GitHub Actions (no PC).

## Decisions (made with the owner)
- Engine: latest **mpv** (libmpv), not ExoPlayer.
- All ads/trackers/Firebase removed. No analytics, no network except what the user opens.
- Kotlin + Jetpack Compose (Material 3), minSdk 29, target/compileSdk 36, arm64-v8a only.
- Package: `app.preciseplayer`. Name: "Precise Player". License GPL-3.0-or-later (mpv/FFmpeg are GPL builds).
- JNI glue (`libplayer.so`) comes from upstream mpv-android and binds to class `is.xyz.mpv.MPVLib`,
  so `app/src/main/java/is/xyz/mpv/MPVLib.kt` must keep that exact package/signatures.

## Build pipeline
- `.github/workflows/native.yml` ("Build engine"): builds libmpv+FFmpeg with upstream buildscripts
  at a pinned mpv-android commit, publishes `engine.zip` to release tag `engine-v1`. Slow (~1h first run),
  rerun only to update the engine (bump ENGINE_TAG + the tag in build.yml).
- `.github/workflows/build.yml` ("Build app"): downloads engine.zip, builds APK + AAB, publishes release.
- Failure logs are put in a `::error` annotation so they're readable through the API.

## Smooth seeking design (the main complaint about the old app)
- Every mpv call runs on one background thread (`MpvCore.post`) — never block the UI thread.
- Seeks are coalesced: at most one seek in flight; newest target wins; next one is sent on
  MPV_EVENT_PLAYBACK_RESTART (or a timeout). Old app queued every slider event.
- Smart scrubbing: fast moves use keyframe seeks, slow/paused finger and release use exact seeks.
- Frame steps use `frame-step N [seek]` with taps accumulated while one is in flight.

## Status / next steps
See PROGRESS.md (kept up to date after every step).
