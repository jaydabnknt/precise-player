# Progress

- [x] Engine workflow (native.yml) — release `engine-v1` built: mpv v0.41.0 + FFmpeg n9.0.2, arm64
- [x] App code: core (MpvCore, Scrubber), player UI, home (recent + video library), settings
- [x] Privacy policy, notices, licence, PLAY_STORE.md, store graphics (store/)
- [x] Upload keystore generated and sent to the owner privately (NOT in repo). Owner adds 4 secrets.
- [x] App workflow green (build.yml) — first build compiled cleanly (build-3)
- [x] Emulator smoke test runs after every build (smoke.yml + tools/smoke.sh): install OK, mpv loads,
      h264 decodes (mediacodec-copy), no crashes. Results are printed as job annotations (readable via API
      without auth: /check-runs/<job>/annotations) and uploaded to release `smoke-test`.
- [x] Owner feedback round 1 (20:00): customizable controls, large portrait frame row, save-frame button
      anywhere, stepping back no longer resumes playback, other controls hide while jogging.
- [ ] Owner tests on phone; fix feedback
- [ ] Owner adds secrets, uploads AAB to Play Console (internal/closed testing)

- [x] Frame stepping verified on emulator (logcat + burned-in frame numbers): single forward steps are muted
      `frame-step 1 mute`, done when time-pos passes the exact base (read synchronously after pausing);
      overshoot gets an exact corrective seek. Back/multi steps are `frame-step N seek`, done on playback-restart.
      Rapid taps coalesce (10 back + 4 fwd -> one `frame-step -5 seek`, exact). Pause is forced before every step.
- [ ] Open question to owner: was the "keeps going back" bug (a) playback resuming or (b) hold-to-repeat?
      If (b): make hold-to-repeat optional/off and lengthen its delay (RepeatButton in SeekBar.kt, 380 ms).
- Known: adb KEYCODE_SPACE never reaches the activity on the emulator (other keys do) — not investigated.

## Ideas not done yet
- Background audio playback (needs a mediaPlayback foreground service + MediaSession)
- Prev/next file in folder, playlists, chapters list
- Thumbnail preview above the seek bar (second mpv instance or MediaMetadataRetriever)
