package app.preciseplayer.mpv

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import app.preciseplayer.Prefs
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvEvent
import `is`.xyz.mpv.MPVLib.MpvFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class Track(
    val id: Int,
    val type: String,
    val title: String?,
    val lang: String?,
    val codec: String?,
    val selected: Boolean,
    val external: Boolean,
) {
    fun label(index: Int): String {
        val parts = listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            lang?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
            codec?.takeIf { it.isNotBlank() },
        )
        return if (parts.isEmpty()) "Track ${index + 1}" else parts.joinToString(" · ")
    }
}

data class PlayerState(
    val created: Boolean = false,
    val fileLoaded: Boolean = false,
    val idle: Boolean = true,
    val title: String = "",
    val duration: Double = 0.0,
    val paused: Boolean = true,
    val buffering: Boolean = false,
    val eof: Boolean = false,
    val seekable: Boolean = true,
    val speed: Double = 1.0,
    val volume: Double = 100.0,
    val volumeMax: Double = 200.0,
    val mute: Boolean = false,
    val containerFps: Double = 0.0,
    val estimatedFps: Double = 0.0,
    val videoW: Int = 0,
    val videoH: Int = 0,
    val rotate: Int = 0,
    val hwdec: String = "",
    val tracks: List<Track> = emptyList(),
    val abLoopA: Double? = null,
    val abLoopB: Double? = null,
    val subDelay: Double = 0.0,
    val audioDelay: Double = 0.0,
    val zoom: Double = 0.0,
    val aspectOverride: String = "no",
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
    val hue: Int = 0,
    val cacheSeconds: Double = 0.0,
    val error: String? = null,
) {
    /** Frame rate used for frame maths: what the file says, else the user's default. */
    val fps: Double
        get() = when {
            containerFps > 1.0 -> containerFps
            estimatedFps > 1.0 -> estimatedFps
            else -> Prefs.defaultFps.value.toDouble()
        }
    val fpsKnown: Boolean get() = containerFps > 1.0 || estimatedFps > 1.0
    val hasVideo: Boolean get() = videoW > 0 && videoH > 0
    /** Display aspect after rotation. */
    val aspect: Double
        get() = if (!hasVideo) 16.0 / 9.0 else if (rotate % 180 == 90) videoH.toDouble() / videoW else videoW.toDouble() / videoH
}

/**
 * The single libmpv instance and everything that talks to it.
 *
 * Every call into mpv runs on one background thread, so the UI thread never waits for the
 * player core (the old app called mpv synchronously from the UI, which froze it while seeking).
 * Seeks and frame steps are coalesced: only one is in flight at a time and newer requests
 * replace older ones, so a fast-moving finger never builds up a backlog of stale seeks.
 */
object MpvCore : MPVLib.EventObserver {
    private const val TAG = "PrecisePlayer"

    private val thread = HandlerThread("mpv-core").apply { start() }
    private val handler = Handler(thread.looper)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    /** Playback position in seconds; updated every frame, kept separate so only position UI recomposes. */
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position

    /** mpv's estimate of the displayed frame number. */
    private val _frame = MutableStateFlow(-1L)
    val frame: StateFlow<Long> = _frame

    // --- everything below is only touched on the mpv-core thread unless noted ---
    private var created = false
    private var surfaceAttached = false
    private var pendingFile: String? = null
    private var vo = "gpu"

    /** Like [post], but skipped when the player isn't running (calling libmpv then would abort). */
    private fun postC(block: () -> Unit) = post { if (created) block() }

    fun post(block: () -> Unit) {
        handler.post {
            try { block() } catch (t: Throwable) { Log.w(TAG, "mpv call failed", t) }
        }
    }

    private fun cmd(vararg args: String) {
        if (!created) return
        MPVLib.command(arrayOf(*args))
    }

    fun command(vararg args: String) = post { cmd(*args) }
    fun setDouble(name: String, v: Double) = post { if (created) MPVLib.setPropertyDouble(name, v) }
    fun setString(name: String, v: String) = post { if (created) MPVLib.setPropertyString(name, v) }
    fun setBool(name: String, v: Boolean) = post { if (created) MPVLib.setPropertyBoolean(name, v) }
    fun setInt(name: String, v: Int) = post { if (created) MPVLib.setPropertyInt(name, v) }

    /** Reads a property on the core thread and hands the result back via [callback] (on that thread). */
    fun <T> query(read: () -> T?, callback: (T?) -> Unit) = post { callback(if (created) read() else null) }

    // ---------------------------------------------------------------- lifecycle

    fun create(context: Context) = post {
        if (created) return@post
        val app = context.applicationContext
        MPVLib.create(app)

        val configDir = File(app.filesDir, "mpv").apply { mkdirs() }
        val watchLater = File(app.filesDir, "watch_later").apply { mkdirs() }
        fun opt(k: String, v: String) { MPVLib.setOptionString(k, v) }

        opt("config", "yes")
        opt("config-dir", configDir.path)
        opt("gpu-shader-cache-dir", app.cacheDir.path)
        opt("icc-cache-dir", app.cacheDir.path)
        opt("profile", "fast")
        vo = if (Prefs.gpuNext.value) "gpu-next" else "gpu"
        opt("vo", vo)
        opt("gpu-context", "android")
        opt("opengl-es", "yes")
        opt("hwdec", if (Prefs.hardwareDecoding.value) "mediacodec,mediacodec-copy" else "no")
        opt("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        if (Prefs.deband.value) opt("deband", "yes")
        opt("ao", "audiotrack,opensles")
        opt("audio-set-media-role", "yes")
        opt("tls-verify", "yes")
        opt("tls-ca-file", File(app.filesDir, "cacert.pem").path)
        opt("input-default-bindings", "yes")

        // Seeking: precise by default; keep a large back-buffer so seeking backwards
        // (and stepping back a frame) re-uses data already read instead of hitting storage again.
        opt("hr-seek", "yes")
        opt("hr-seek-framedrop", if (Prefs.accurateFrameSteps.value) "no" else "yes")
        opt("cache", "yes")
        opt("demuxer-seekable-cache", "yes")
        opt("demuxer-max-bytes", "${96 * 1024 * 1024}")
        opt("demuxer-max-back-bytes", "${96 * 1024 * 1024}")
        opt("demuxer-readahead-secs", "20")

        // We draw our own controls
        opt("osd-bar", "no")
        opt("osd-on-seek", "no")
        opt("osd-level", "1")
        opt("keep-open", "yes")
        opt("keep-open-pause", "yes")

        // Volume is separate from the device volume and may go above 100%
        opt("volume-max", Prefs.volumeMax.value.toString())
        if (Prefs.rememberVolume.value) opt("volume", Prefs.lastVolume.value.toString())

        // Resume where you left off
        opt("watch-later-directory", watchLater.path)
        opt("resume-playback", if (Prefs.resume.value) "yes" else "no")
        opt("watch-later-options", "start,aid,sid,sub-delay,audio-delay")
        opt("save-position-on-quit", "no")

        opt("screenshot-directory", app.cacheDir.path)
        opt("screenshot-format", "png")

        MPVLib.init()

        opt("force-window", "no")
        opt("idle", "yes")

        MPVLib.addObserver(this)
        observeAll()
        created = true
        _state.value = PlayerState(created = true, volumeMax = Prefs.volumeMax.value.toDouble())
    }

    fun destroy() = post {
        if (!created) return@post
        synchronized(gate) { inFlight = Op.NONE; pendingSeek = null; pendingSteps = 0; settled.clear() }
        MPVLib.removeObserver(this)
        created = false
        surfaceAttached = false
        pendingFile = null
        MPVLib.destroy()
        _state.value = PlayerState()
        _position.value = 0.0
        _frame.value = -1
    }

    fun setDisplayFps(fps: Float) = post { if (created) MPVLib.setOptionString("display-fps-override", fps.toString()) }

    fun attachSurface(surface: Surface) = post {
        if (!created) return@post
        MPVLib.attachSurface(surface)
        MPVLib.setOptionString("force-window", "yes")
        surfaceAttached = true
        val f = pendingFile
        if (f != null) {
            pendingFile = null
            cmd("loadfile", f)
        } else {
            MPVLib.setPropertyString("vo", vo)
        }
    }

    fun setSurfaceSize(w: Int, h: Int) = post { if (created) MPVLib.setPropertyString("android-surface-size", "${w}x$h") }

    /** Must finish before SurfaceHolder.surfaceDestroyed returns, so this blocks (briefly). */
    fun detachSurfaceBlocking() {
        val latch = CountDownLatch(1)
        post {
            try {
                if (created && surfaceAttached) {
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.setPropertyString("force-window", "no")
                    MPVLib.detachSurface()
                }
            } finally {
                surfaceAttached = false
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
    }

    fun loadFile(path: String) = post {
        if (!created) return@post
        _state.update { it.copy(fileLoaded = false, error = null, eof = false) }
        if (surfaceAttached) cmd("loadfile", path, "replace") else pendingFile = path
    }

    fun saveResumePoint() = post { if (created && Prefs.resume.value && _state.value.fileLoaded) cmd("write-watch-later-config") }

    // ---------------------------------------------------------------- playback

    fun setPaused(p: Boolean) = setBool("pause", p)
    fun togglePause() = post {
        if (!created) return@post
        val s = _state.value
        if (s.eof && s.paused) { cmd("seek", "0", "absolute+exact"); MPVLib.setPropertyBoolean("pause", false) }
        else cmd("cycle", "pause")
    }
    fun setSpeed(s: Double) = setDouble("speed", s.coerceIn(0.01, 100.0))
    fun setVolume(v: Double) {
        val c = v.coerceIn(0.0, _state.value.volumeMax)
        _state.update { it.copy(volume = c) }          // instant UI feedback
        setDouble("volume", c)
    }
    fun setVolumeMax(v: Int) { setString("volume-max", v.toString()); _state.update { it.copy(volumeMax = v.toDouble()) } }
    fun setMute(m: Boolean) = setBool("mute", m)

    // ---------------------------------------------------------------- coalesced seeking

    private enum class Op { NONE, SEEK, STEP }
    private data class SeekReq(val t: Double, val exact: Boolean)

    private val gate = Any()
    @Volatile private var inFlight = Op.NONE
    private var opToken = 0
    private var pendingSeek: SeekReq? = null
    private var pendingSteps = 0
    private var lastExactTarget = Double.NaN
    private val settled = mutableListOf<() -> Unit>()

    /** True while a seek or frame step is being processed or waiting. Any thread. */
    val busy: Boolean get() = synchronized(gate) { inFlight != Op.NONE || pendingSeek != null || pendingSteps != 0 }

    /**
     * Seek to [t] seconds. If a seek is already running, this replaces whatever was waiting
     * behind it, so only the newest target is ever processed next.
     */
    fun seekTo(t: Double, exact: Boolean) {
        val d = _state.value.duration
        val target = if (d > 0) t.coerceIn(0.0, d) else t.coerceAtLeast(0.0)
        synchronized(gate) {
            pendingSteps = 0
            pendingSeek = SeekReq(target, exact)
            if (inFlight == Op.NONE) dispatchLocked()
        }
    }

    fun seekBy(delta: Double, exact: Boolean = true) = seekTo(_position.value + delta, exact)

    /** Step [n] frames (negative = backwards). Taps made while one step runs are added together. */
    fun step(n: Int) {
        if (n == 0) return
        synchronized(gate) {
            pendingSteps += n
            if (inFlight == Op.NONE) dispatchLocked()
        }
    }

    /** Runs [block] (on the core thread) once no seek or step is running or waiting. */
    fun whenSettled(block: () -> Unit) {
        synchronized(gate) {
            if (inFlight == Op.NONE && pendingSeek == null && pendingSteps == 0) post(block)
            else settled.add(block)
        }
    }

    private fun dispatchLocked() {
        val seek = pendingSeek
        val steps = pendingSteps
        val token = ++opToken
        when {
            seek != null -> {
                pendingSeek = null
                inFlight = Op.SEEK
                if (seek.exact) lastExactTarget = seek.t
                post { cmd("seek", fmt(seek.t), if (seek.exact) "absolute+exact" else "absolute+keyframes") }
                handler.postDelayed({ opDone(token) }, if (seek.exact) 1500 else 800)
            }
            steps != 0 -> {
                pendingSteps = 0
                inFlight = Op.STEP
                post {
                    when {
                        steps == 1 -> cmd("frame-step")
                        steps in 2..4 -> cmd("frame-step", steps.toString(), "play")
                        else -> cmd("frame-step", steps.toString(), "seek")
                    }
                }
                handler.postDelayed({ opDone(token) }, 1500)
            }
            else -> {
                inFlight = Op.NONE
                if (settled.isNotEmpty()) {
                    val list = settled.toList(); settled.clear()
                    list.forEach { post(it) }
                }
            }
        }
    }

    private fun opDone(token: Int? = null) {
        synchronized(gate) {
            if (inFlight == Op.NONE) return
            if (token != null && token != opToken) return
            inFlight = Op.NONE
            dispatchLocked()
        }
    }

    private fun fmt(t: Double) = String.format(Locale.US, "%.6f", t)

    // ---------------------------------------------------------------- misc commands

    fun setAbLoop() = postC {
        val s = _state.value
        val p = _position.value
        when {
            s.abLoopA == null -> MPVLib.setPropertyString("ab-loop-a", fmt(p))
            s.abLoopB == null -> {
                if (p > s.abLoopA) MPVLib.setPropertyString("ab-loop-b", fmt(p))
                else { MPVLib.setPropertyString("ab-loop-b", fmt(s.abLoopA)); MPVLib.setPropertyString("ab-loop-a", fmt(p)) }
            }
            else -> { MPVLib.setPropertyString("ab-loop-a", "no"); MPVLib.setPropertyString("ab-loop-b", "no") }
        }
    }

    fun selectTrack(type: String, id: Int?) = postC {
        val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
        MPVLib.setPropertyString(prop, id?.toString() ?: "no")
        readTracks()
    }

    fun addSubtitle(path: String) = command("sub-add", path, "select")

    fun screenshot(file: File, done: (Boolean) -> Unit) = postC {
        file.delete()
        cmd("screenshot-to-file", file.path, "video")
        done(file.exists() && file.length() > 0)
    }

    fun toggleStats() = command("script-binding", "stats/display-stats-toggle")

    fun resetPicture() = postC {
        for (p in arrayOf("brightness", "contrast", "saturation", "gamma", "hue")) MPVLib.setPropertyInt(p, 0)
    }

    // ---------------------------------------------------------------- observation

    private fun observeAll() {
        val d = MpvFormat.MPV_FORMAT_DOUBLE
        val f = MpvFormat.MPV_FORMAT_FLAG
        val i = MpvFormat.MPV_FORMAT_INT64
        val s = MpvFormat.MPV_FORMAT_STRING
        val n = MpvFormat.MPV_FORMAT_NONE
        listOf(
            "time-pos" to d, "duration/full" to d, "pause" to f, "paused-for-cache" to f, "eof-reached" to f,
            "seekable" to f, "speed" to d, "volume" to d, "volume-max" to d, "mute" to f,
            "container-fps" to d, "estimated-vf-fps" to d, "estimated-frame-number" to i,
            "video-params/w" to i, "video-params/h" to i, "video-params/rotate" to i,
            "media-title" to s, "hwdec-current" to s, "track-list" to n,
            "ab-loop-a" to s, "ab-loop-b" to s, "sub-delay" to d, "audio-delay" to d,
            "video-zoom" to d, "video-aspect-override" to s,
            "brightness" to i, "contrast" to i, "saturation" to i, "gamma" to i, "hue" to i,
            "demuxer-cache-time" to d, "idle-active" to f,
        ).forEach { (name, fmt) -> MPVLib.observeProperty(name, fmt) }
    }

    private var lastPosForStep = Double.NaN

    override fun eventProperty(property: String) {
        if (property == "track-list") post { readTracks() }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "estimated-frame-number" -> _frame.value = value
            "video-params/w" -> _state.update { it.copy(videoW = value.toInt()) }
            "video-params/h" -> _state.update { it.copy(videoH = value.toInt()) }
            "video-params/rotate" -> _state.update { it.copy(rotate = value.toInt()) }
            "brightness" -> _state.update { it.copy(brightness = value.toInt()) }
            "contrast" -> _state.update { it.copy(contrast = value.toInt()) }
            "saturation" -> _state.update { it.copy(saturation = value.toInt()) }
            "gamma" -> _state.update { it.copy(gamma = value.toInt()) }
            "hue" -> _state.update { it.copy(hue = value.toInt()) }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> _state.update { it.copy(paused = value) }
            "paused-for-cache" -> _state.update { it.copy(buffering = value) }
            "eof-reached" -> _state.update { it.copy(eof = value) }
            "seekable" -> _state.update { it.copy(seekable = value) }
            "mute" -> _state.update { it.copy(mute = value) }
            "idle-active" -> _state.update { it.copy(idle = value) }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "media-title" -> _state.update { it.copy(title = value) }
            "hwdec-current" -> _state.update { it.copy(hwdec = value) }
            "ab-loop-a" -> _state.update { it.copy(abLoopA = value.toDoubleOrNull()) }
            "ab-loop-b" -> _state.update { it.copy(abLoopB = value.toDoubleOrNull()) }
            "video-aspect-override" -> _state.update { it.copy(aspectOverride = value) }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                _position.value = value
                // a "play"-mode frame step is done as soon as the next frame is shown
                if (inFlight == Op.STEP && value != lastPosForStep) {
                    lastPosForStep = value
                    opDone()
                }
                lastPosForStep = value
            }
            "duration/full" -> _state.update { it.copy(duration = value) }
            "speed" -> _state.update { it.copy(speed = value) }
            "volume" -> _state.update { it.copy(volume = value) }
            "volume-max" -> _state.update { it.copy(volumeMax = value) }
            "container-fps" -> _state.update { it.copy(containerFps = value) }
            "estimated-vf-fps" -> _state.update { it.copy(estimatedFps = value) }
            "sub-delay" -> _state.update { it.copy(subDelay = value) }
            "audio-delay" -> _state.update { it.copy(audioDelay = value) }
            "video-zoom" -> _state.update { it.copy(zoom = value) }
            "demuxer-cache-time" -> _state.update { it.copy(cacheSeconds = value) }
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> opDone()
            MpvEvent.MPV_EVENT_FILE_LOADED -> {
                _state.update { it.copy(fileLoaded = true, error = null) }
                post { readTracks() }
            }
            MpvEvent.MPV_EVENT_START_FILE -> {
                _state.update { it.copy(fileLoaded = false, eof = false, abLoopA = null, abLoopB = null) }
                _frame.value = -1
            }
            MpvEvent.MPV_EVENT_END_FILE -> {
                synchronized(gate) { inFlight = Op.NONE; pendingSeek = null; pendingSteps = 0 }
                post {
                    // if nothing ever loaded, tell the user instead of showing a black screen
                    if (!_state.value.fileLoaded) _state.update { it.copy(error = "This file couldn't be opened.") }
                }
            }
        }
    }

    private fun readTracks() {
        if (!created) return
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val list = ArrayList<Track>(count)
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            list += Track(
                id = id,
                type = type,
                title = MPVLib.getPropertyString("track-list/$i/title"),
                lang = MPVLib.getPropertyString("track-list/$i/lang"),
                codec = MPVLib.getPropertyString("track-list/$i/codec"),
                selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false,
                external = MPVLib.getPropertyBoolean("track-list/$i/external") ?: false,
            )
        }
        _state.update { it.copy(tracks = list) }
    }

    /** Elapsed realtime, for velocity maths in the UI. */
    fun now(): Long = SystemClock.uptimeMillis()
}
