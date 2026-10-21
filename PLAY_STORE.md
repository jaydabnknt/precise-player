# Publishing on Google Play

Everything needed for the Play Console, in order.

## 1. Upload key (once)
Google Play re-signs the app with its own key ("Play App Signing"); you sign uploads with an *upload key*.
The build signs with it automatically once these four **repository secrets** exist
(GitHub → repo → Settings → Secrets and variables → Actions → New repository secret):

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | contents of `upload-keystore.b64.txt` (the long text, all of it) |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | same as the keystore password |

Keep the keystore file and password somewhere safe (not in this public repo). If it is ever lost,
Play Console lets you reset the upload key.

Then run **Actions → Build app → Run workflow**. The release's `PrecisePlayer.aab` is what you upload.
(Until the secrets exist, builds are signed with the debug key: fine to install, but not for Play.)

## 2. Play Console
1. Create app → name **Precise Player**, App, Free.
2. **Testing → Internal testing** (or Closed testing) → Create release → upload `PrecisePlayer.aab`.
   New personal developer accounts must run a closed test with at least 12 testers for 14 days
   before production access.
3. **App content**:
   - Privacy policy URL: `https://github.com/jaydabnknt/precise-player/blob/main/PRIVACY.md`
   - Ads: **No ads**
   - App access: all functionality available without login
   - Content rating questionnaire: category *Utility/Productivity/Other*; answer **No** to everything → rated Everyone/PEGI 3
   - Target audience: 13+ (not designed for children)
   - Data safety: **No data collected, no data shared.** Data is not encrypted in transit is irrelevant (none sent);
     "Users can request data deletion": not applicable (no data collected).
   - **Photo and video permissions** declaration (READ_MEDIA_VIDEO): core purpose is playing the user's videos —
     *"Precise Player is a video player; it lists the videos on the device so the user can browse and play them."*
   - Government apps / financial / health: No.
4. **Store listing**: texts below, graphics in `store/`.

## 3. Store listing text

**App name** (30): `Precise Player`

**Short description** (80):
`Frame-accurate video player: smooth scrubbing, frame stepping, precise volume.`

**Full description** (4000):
```
Precise Player is a video player built for exact control. Scrub through a video and see the actual frame under your finger, step forward or back one frame at a time, and set volume and seek amounts down to the millisecond.

SMOOTH, FRAME-ACCURATE SEEKING
• Scrubbing stays smooth on long and high-resolution videos: the preview keeps up with your finger and lands on the exact frame when you slow down
• Slide your finger up while dragging the seek bar for ½, ¼ or 1/10-speed fine scrubbing
• Step one frame forward or back, hold to keep stepping, or drag the jog strip to move frame by frame
• Go straight to a timestamp (1:23.456) or a frame number
• Time shown to the millisecond, with the current frame number and frame rate
• A-B loop to repeat any section

PRECISE VOLUME
• Player volume separate from your phone's, in 1% steps
• Boost quiet videos above 100% (up to 1000%)
• Volume buttons and swipe gestures control it

GESTURES
• Double-tap left/right to jump back/forward (amount in milliseconds or frames)
• Swipe sideways to seek, up/down for brightness and volume
• Press and hold for fast playback
• Pinch to zoom and pan

PLAYS ALMOST ANYTHING
• Powered by mpv and FFmpeg: MKV, MP4, WebM, AVI, MOV, TS, FLV and more; H.264, HEVC, AV1, VP9 and more
• Hardware decoding, with a software fallback
• Multiple audio tracks, subtitles (SRT, ASS/SSA with full styling, PGS…), external subtitle files, audio and subtitle delay
• Playback speed from 0.1× to 4× in 0.01× steps
• Aspect ratio, crop to fill, brightness, contrast, saturation and gamma
• Save the exact current frame as a full-resolution image
• Picture-in-picture, resume where you left off, network streams

NO ADS. NO TRACKING.
No ads, no analytics, no account. Free and open-source software (GPL v3).
```

**Category**: Video Players & Editors. **Tags**: video player, frame by frame.

## 4. Graphics (in `store/`)
- `icon-512.png` — app icon 512×512
- `feature-graphic.png` — 1024×500
- Phone screenshots: take 2–8 on your phone (power + volume down) while playing a video:
  controls visible, fine scrubbing, the speed sheet, the Videos tab. Upload as-is.
