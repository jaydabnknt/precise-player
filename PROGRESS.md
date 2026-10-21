# Progress

- [x] Engine workflow (native.yml) — release `engine-v1` built: mpv v0.41.0 + FFmpeg n9.0.2, arm64
- [x] App code: core (MpvCore, Scrubber), player UI, home (recent + video library), settings
- [x] Privacy policy, notices, licence, PLAY_STORE.md, store graphics (store/)
- [x] Upload keystore generated and sent to the owner privately (NOT in repo). Owner adds 4 secrets.
- [x] App workflow green (build.yml) — first build compiled cleanly (build-3)
- [ ] Emulator smoke test (smoke.yml + tools/smoke.sh) → results in release `smoke-test`
- [ ] Owner tests on phone; fix feedback
- [ ] Owner adds secrets, uploads AAB to Play Console (internal/closed testing)

## Ideas not done yet
- Background audio playback (needs a mediaPlayback foreground service + MediaSession)
- Prev/next file in folder, playlists, chapters list
- Thumbnail preview above the seek bar (second mpv instance or MediaMetadataRetriever)
