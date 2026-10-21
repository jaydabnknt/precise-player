package app.preciseplayer.mpv

import android.os.Handler
import android.os.Looper
import app.preciseplayer.Prefs
import app.preciseplayer.ScrubMode
import kotlin.math.abs

/**
 * One scrubbing session (seek bar drag, swipe on the video, jog...).
 *
 * Smart mode: while the finger moves fast, cheap keyframe seeks give instant feedback;
 * as soon as it slows down or stops for a moment, an exact seek lands on the precise frame.
 * The playback is paused during the drag and resumed after the final seek has landed.
 */
class Scrubber {
    private val main = Handler(Looper.getMainLooper())
    var active = false
        private set
    var target = 0.0
        private set
    private var wasPlaying = false
    private var lastT = 0.0
    private var lastTime = 0L
    private var lastWasExact = true

    private val settle = Runnable {
        if (active && !lastWasExact) {
            lastWasExact = true
            MpvCore.seekTo(target, true)
        }
    }

    fun start(from: Double) {
        if (active) return
        active = true
        target = from
        lastT = from
        lastTime = MpvCore.now()
        lastWasExact = true
        wasPlaying = !MpvCore.state.value.paused
        if (wasPlaying) MpvCore.setPaused(true)
    }

    fun update(t: Double) {
        if (!active) return
        val d = MpvCore.state.value.duration
        val clamped = if (d > 0) t.coerceIn(0.0, d) else t.coerceAtLeast(0.0)
        val now = MpvCore.now()
        val dt = (now - lastTime).coerceAtLeast(1)
        // how many seconds of video per second of finger movement
        val velocity = abs(clamped - lastT) * 1000.0 / dt
        target = clamped
        lastT = clamped
        lastTime = now
        val exact = when (Prefs.scrubMode.value) {
            ScrubMode.EXACT -> true
            ScrubMode.KEYFRAMES -> false
            ScrubMode.SMART -> velocity < 4.0
        }
        lastWasExact = exact
        MpvCore.seekTo(clamped, exact)
        main.removeCallbacks(settle)
        main.postDelayed(settle, 120)
    }

    fun end(resume: Boolean = true) {
        if (!active) return
        main.removeCallbacks(settle)
        active = false
        MpvCore.seekTo(target, Prefs.scrubMode.value != ScrubMode.KEYFRAMES)
        if (wasPlaying && resume) MpvCore.whenSettled { MpvCore.setPaused(false) }
    }
}
