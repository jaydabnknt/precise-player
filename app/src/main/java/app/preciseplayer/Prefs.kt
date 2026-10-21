package app.preciseplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** How scrubbing previews frames while the finger is moving. */
enum class ScrubMode { SMART, EXACT, KEYFRAMES }

enum class OrientationMode { AUTO, SENSOR, LANDSCAPE, PORTRAIT }

enum class FrameRowSize { AUTO, NORMAL, LARGE }

/** Every button that can be placed in the player's top bar, centre or bottom bar. */
enum class Ctl(val label: String) {
    PLAY_PAUSE("Play / pause"),
    SEEK_BACK("Rewind"),
    SEEK_FWD("Fast-forward"),
    FRAME_BACK("Previous frame"),
    FRAME_FWD("Next frame"),
    SAVE_FRAME("Save frame"),
    AUDIO("Audio track"),
    SUBS("Subtitles"),
    SPEED("Playback speed"),
    VOLUME("Volume"),
    MUTE("Mute"),
    GO_TO("Go to time / frame"),
    AB_LOOP("A-B loop"),
    VIDEO("Video & picture"),
    FILL("Fit / fill screen"),
    ROTATE("Rotate screen"),
    LOCK("Lock controls"),
    PIP("Picture-in-picture"),
    STATS("Statistics"),
    SETTINGS("Settings");
}

enum class Slot(val label: String) { TOP("Top bar"), CENTER("Centre"), BOTTOM("Bottom bar") }

/** An amount of time to jump, either in milliseconds or in video frames. */
data class SeekAmount(val value: Int, val frames: Boolean) {
    fun seconds(fps: Double): Double = if (frames) value / fps.coerceAtLeast(1.0) else value / 1000.0
    fun label(): String = if (frames) "$value ${if (value == 1) "frame" else "frames"}" else formatMs(value)

    companion object {
        fun formatMs(ms: Int): String = when {
            ms % 60000 == 0 && ms >= 60000 -> "${ms / 60000} min"
            ms % 1000 == 0 -> "${ms / 1000} s"
            ms >= 1000 -> "%.3f".format(java.util.Locale.US, ms / 1000.0).trimEnd('0').trimEnd('.') + " s"
            else -> "$ms ms"
        }
    }
}

/**
 * App settings, backed by SharedPreferences and exposed as Compose state so screens update live.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    abstract class Pref<T>(val key: String, val default: T) {
        protected abstract fun read(): T
        protected abstract fun write(v: T)
        private var state by mutableStateOf<T?>(null)
        var value: T
            get() = state ?: read().also { state = it }
            set(v) { state = v; write(v) }
    }

    class BoolPref(key: String, default: Boolean) : Pref<Boolean>(key, default) {
        override fun read() = sp.getBoolean(key, default)
        override fun write(v: Boolean) = sp.edit { putBoolean(key, v) }
    }

    class IntPref(key: String, default: Int) : Pref<Int>(key, default) {
        override fun read() = sp.getInt(key, default)
        override fun write(v: Int) = sp.edit { putInt(key, v) }
    }

    class FloatPref(key: String, default: Float) : Pref<Float>(key, default) {
        override fun read() = sp.getFloat(key, default)
        override fun write(v: Float) = sp.edit { putFloat(key, v) }
    }

    class StringPref(key: String, default: String) : Pref<String>(key, default) {
        override fun read() = sp.getString(key, default) ?: default
        override fun write(v: String) = sp.edit { putString(key, v) }
    }

    class EnumPref<E : Enum<E>>(key: String, default: E, private val values: Array<E>) : Pref<E>(key, default) {
        override fun read() = sp.getString(key, null)?.let { n -> values.firstOrNull { it.name == n } } ?: default
        override fun write(v: E) = sp.edit { putString(key, v.name) }
    }

    class CtlListPref(key: String, default: List<Ctl>) : Pref<List<Ctl>>(key, default) {
        override fun read(): List<Ctl> = sp.getString(key, null)?.let { str ->
            str.split(',').mapNotNull { n -> Ctl.values().firstOrNull { it.name == n } }
        } ?: default
        override fun write(v: List<Ctl>) = sp.edit { putString(key, v.joinToString(",") { it.name }) }
    }

    class AmountPref(key: String, default: SeekAmount) : Pref<SeekAmount>(key, default) {
        override fun read() = SeekAmount(sp.getInt("$key.v", default.value), sp.getBoolean("$key.f", default.frames))
        override fun write(v: SeekAmount) = sp.edit { putInt("$key.v", v.value); putBoolean("$key.f", v.frames) }
    }

    // Seeking
    val doubleTapSeek = AmountPref("double_tap_seek", SeekAmount(10_000, false))
    val buttonSeek = AmountPref("button_seek", SeekAmount(5_000, false))
    val scrubMode = EnumPref("scrub_mode", ScrubMode.SMART, ScrubMode.values())
    /** Milliseconds of video per dp of horizontal swipe on the video. */
    val swipeSeekMsPerDp = IntPref("swipe_seek_ms_per_dp", 100)
    val accurateFrameSteps = BoolPref("strict_frame_steps", false)
    val showMilliseconds = BoolPref("show_ms", true)
    val showFrameNumber = BoolPref("show_frame", true)
    val defaultFps = FloatPref("default_fps", 30f)
    val showJog = BoolPref("show_jog", true)

    val frameStepCount = IntPref("frame_step_count", 1)
    val jogTickDp = IntPref("jog_tick_dp", 12)
    val fineScrub = BoolPref("fine_scrub", true)
    val showScrubOverlay = BoolPref("scrub_overlay", true)

    // Controls layout
    val topButtons = CtlListPref("ctl_top", listOf(Ctl.AUDIO, Ctl.SUBS, Ctl.SPEED))
    val centerButtons = CtlListPref("ctl_center", listOf(Ctl.SEEK_BACK, Ctl.PLAY_PAUSE, Ctl.SEEK_FWD))
    val bottomButtons = CtlListPref("ctl_bottom", listOf(Ctl.SAVE_FRAME, Ctl.VOLUME, Ctl.ROTATE, Ctl.LOCK))
    val showFrameRow = BoolPref("frame_row", true)
    val frameRowSize = EnumPref("frame_row_size", FrameRowSize.AUTO, FrameRowSize.values())
    val hideDelayMs = IntPref("hide_delay_ms", 3500)
    val controlsDim = FloatPref("controls_dim", 0.8f)
    val largeSeekBar = BoolPref("large_seekbar", false)
    val showRemaining = BoolPref("show_remaining", false)
    val showTitle = BoolPref("show_title", true)

    fun buttons(slot: Slot) = when (slot) { Slot.TOP -> topButtons; Slot.CENTER -> centerButtons; Slot.BOTTOM -> bottomButtons }

    // Gestures
    val gestureSeek = BoolPref("g_seek", true)
    val gestureVolume = BoolPref("g_volume", true)
    val gestureBrightness = BoolPref("g_brightness", true)
    val gestureDoubleTapSeek = BoolPref("g_dtap_seek", true)
    val gestureDoubleTapPause = BoolPref("g_dtap_pause", true)
    val gesturePinch = BoolPref("g_pinch", true)
    val gestureLongPress = BoolPref("g_long_press", true)

    // Subtitles
    val subScale = FloatPref("sub_scale", 1f)
    val subPos = IntPref("sub_pos", 100)

    // Playback
    val rememberSpeed = BoolPref("remember_speed", false)
    val lastSpeed = FloatPref("last_speed", 1f)
    val resume = BoolPref("resume", true)
    val autoPip = BoolPref("auto_pip", true)
    val orientation = EnumPref("orientation", OrientationMode.AUTO, OrientationMode.values())
    val longPressSpeed = FloatPref("long_press_speed", 2f)
    val pauseOnHeadphones = BoolPref("pause_on_headphones", true)

    // Audio
    val volumeKeysControlPlayer = BoolPref("volume_keys_player", true)
    val volumeStep = FloatPref("volume_step", 1f)
    val volumeMax = IntPref("volume_max", 200)
    val rememberVolume = BoolPref("remember_volume", true)
    val lastVolume = FloatPref("last_volume", 100f)

    // Video
    val hardwareDecoding = BoolPref("hwdec", true)
    val gpuNext = BoolPref("gpu_next", false)
    val deband = BoolPref("deband", false)

    // Look
    val dynamicColor = BoolPref("dynamic_color", false)
    val accent = IntPref("accent", 0)

    // Library
    val folderFilter = StringPref("folder_filter", "")
}

/** Recently played items, newest first, stored as JSON in SharedPreferences. */
object Recents {
    data class Item(val uri: String, val title: String, val position: Double, val duration: Double, val time: Long)

    private const val KEY = "recents"
    private const val MAX = 40
    private lateinit var sp: SharedPreferences
    private val _items = mutableStateOf<List<Item>>(emptyList())
    val items: List<Item> get() = _items.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
        _items.value = load()
    }

    private fun load(): List<Item> = try {
        val arr = JSONArray(sp.getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(o.getString("u"), o.optString("t"), o.optDouble("p", 0.0), o.optDouble("d", 0.0), o.optLong("w"))
        }
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<Item>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("u", it.uri).put("t", it.title).put("p", it.position).put("d", it.duration).put("w", it.time)) }
        sp.edit { putString(KEY, arr.toString()) }
    }

    fun get(uri: String): Item? = items.firstOrNull { it.uri == uri }

    fun touch(uri: String, title: String?, position: Double? = null, duration: Double? = null) {
        val old = get(uri)
        val item = Item(
            uri,
            title?.takeIf { it.isNotBlank() } ?: old?.title ?: uri.substringAfterLast('/'),
            position ?: old?.position ?: 0.0,
            duration ?: old?.duration ?: 0.0,
            System.currentTimeMillis(),
        )
        save((listOf(item) + items.filter { it.uri != uri }).take(MAX))
    }

    fun remove(uri: String) = save(items.filter { it.uri != uri })
    fun clear() = save(emptyList())
}
