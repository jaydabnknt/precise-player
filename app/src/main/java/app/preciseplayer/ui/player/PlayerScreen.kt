package app.preciseplayer.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.preciseplayer.Prefs
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.mpv.MpvSurfaceView
import app.preciseplayer.mpv.PlayerState
import app.preciseplayer.mpv.Scrubber
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.theme.Tabular
import app.preciseplayer.ui.theme.TimeStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

enum class Sheet { AUDIO, SUBS, SPEED, VOLUME, VIDEO, MORE }
enum class ScrubSource { BAR, SWIPE }

data class Feedback(
    val icon: ImageVector?,
    val text: String,
    val sub: String? = null,
    val progress: Float? = null,
    val id: Long = 0,
)

/** Things the player screen needs from its activity. */
interface PlayerHost {
    fun back()
    fun enterPip()
    fun rotate()
    fun screenshot()
    fun openSettings()
    fun customizeControls()
    fun pickSubtitle()
    fun getBrightness(): Float
    fun setBrightness(v: Float)
}

class PlayerUiState {
    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var sheet by mutableStateOf<Sheet?>(null)
    var goToOpen by mutableStateOf(false)
    var feedback by mutableStateOf<Feedback?>(null)
    var scrubTarget by mutableStateOf<Double?>(null)
    var scrubStart by mutableStateOf(0.0)
    var scrubSource by mutableStateOf(ScrubSource.BAR)
    var scrubFine by mutableStateOf(1f)
    var inPip by mutableStateOf(false)
    /** True while the jog strip is being dragged: everything else is hidden. */
    var jogging by mutableStateOf(false)
    var fill by mutableStateOf(false)
        private set

    fun changeFill(on: Boolean) {
        fill = on
        MpvCore.setDouble("panscan", if (on) 1.0 else 0.0)
        show(null, if (on) "Fill screen" else "Fit to screen")
    }
    var interaction by mutableLongStateOf(0L)
    private var feedbackId = 0L

    fun poke() { interaction = SystemClock.uptimeMillis() }

    fun show(icon: ImageVector?, text: String, sub: String? = null, progress: Float? = null) {
        feedback = Feedback(icon, text, sub, progress, ++feedbackId)
    }

    fun beginScrub(source: ScrubSource, from: Double) {
        scrubSource = source
        scrubStart = from
        scrubFine = 1f
        poke()
    }

    fun endScrub() {
        scrubTarget = null
        scrubFine = 1f
        poke()
    }
}

@Composable
fun PlayerScreen(ui: PlayerUiState, host: PlayerHost) {
    val s by MpvCore.state.collectAsState()
    val scrubber = remember { Scrubber() }
    val density = LocalDensity.current

    // auto-hide the controls a few seconds after the last interaction while playing
    LaunchedEffect(ui.controlsVisible, ui.interaction, s.paused, ui.sheet, ui.scrubTarget, ui.jogging) {
        val hide = Prefs.hideDelayMs.value
        if (hide > 0 && ui.controlsVisible && !s.paused && ui.sheet == null && ui.scrubTarget == null && !ui.goToOpen && !ui.jogging) {
            delay(hide.toLong())
            ui.controlsVisible = false
        }
    }
    LaunchedEffect(ui.feedback?.id) {
        val f = ui.feedback
        if (f != null && f.id >= 0) {
            delay(900)
            if (ui.scrubTarget == null || ui.scrubSource != ScrubSource.SWIPE) ui.feedback = null
        }
    }

    val gestures = remember(host) { PlayerGestures(ui, host, scrubber, density) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { MpvSurfaceView(it) }, modifier = Modifier.fillMaxSize())

        if (!ui.inPip) {
            Box(Modifier.fillMaxSize().playerGestures(gestures))

            if (s.buffering || (!s.fileLoaded && s.error == null)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White.copy(alpha = 0.8f))
            }
            s.error?.let { ErrorCard(it, host, Modifier.align(Alignment.Center)) }

            AnimatedVisibility(ui.controlsVisible && !ui.locked, enter = fadeIn(), exit = fadeOut()) {
                Controls(s, ui, host, scrubber, onResetZoom = { gestures.resetZoom() })
            }
            AnimatedVisibility(ui.locked && ui.controlsVisible, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)) {
                FilledIconButton(
                    onClick = { ui.locked = false; ui.poke() },
                    modifier = Modifier.padding(start = 24.dp).size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
                ) { Icon(Icons.Rounded.LockOpen, "Unlock") }
            }

            if (Prefs.showScrubOverlay.value) ScrubOverlay(s, ui, Modifier.align(Alignment.Center))
            if (ui.scrubTarget == null) FeedbackPill(ui.feedback, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))

            Sheets(s, ui, host)
            if (ui.goToOpen) GoToDialog(s, ui)
        }
    }
}

private class PlayerGestures(
    private val ui: PlayerUiState,
    private val host: PlayerHost,
    private val scrubber: Scrubber,
    private val density: Density,
) : GestureCallbacks {
    var startValue = 0f
    var speedBefore = 1.0
    var boosting = false
    var zoomScale = 1f
    var panX = 0f
    var panY = 0f
    var tapSeekAccum = 0.0

    override fun onTap() {
        if (ui.sheet != null) { ui.sheet = null; return }
        ui.controlsVisible = !ui.controlsVisible
        ui.poke()
    }

    override fun onDoubleTap(zone: Int, repeat: Boolean) {
        if (ui.locked) return
        val st = MpvCore.state.value
        if (zone == 0) {
            if (!Prefs.gestureDoubleTapPause.value) { onTap(); return }
            MpvCore.togglePause()
            ui.show(if (st.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (st.paused) "Play" else "Pause")
            return
        }
        if (!Prefs.gestureDoubleTapSeek.value) { onTap(); return }
        val amount = Prefs.doubleTapSeek.value
        val secs = amount.seconds(st.fps) * zone
        tapSeekAccum = if (repeat) tapSeekAccum + secs else secs
        MpvCore.seekRelative(secs)
        ui.show(
            if (zone < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
            Format.delta(tapSeekAccum),
            Format.time(MpvCore.lastTarget(), Prefs.showMilliseconds.value),
        )
    }

    override fun onLongPressStart() {
        if (ui.locked || !Prefs.gestureLongPress.value) return
        val st = MpvCore.state.value
        if (st.paused) return
        speedBefore = st.speed
        boosting = true
        MpvCore.setSpeed(Prefs.longPressSpeed.value.toDouble())
        ui.feedback = Feedback(Icons.Rounded.Speed, Format.speed(Prefs.longPressSpeed.value.toDouble()), "Release to go back", id = -1)
    }

    override fun onLongPressEnd() {
        if (!boosting) return
        boosting = false
        MpvCore.setSpeed(speedBefore)
        ui.feedback = null
    }

    override fun dragAllowed(kind: DragKind) = !ui.locked && when (kind) {
        DragKind.SEEK -> Prefs.gestureSeek.value
        DragKind.VOLUME -> Prefs.gestureVolume.value
        DragKind.BRIGHTNESS -> Prefs.gestureBrightness.value
    }

    override fun pinchAllowed() = !ui.locked && Prefs.gesturePinch.value

    override fun onDragStart(kind: DragKind) {
        val st = MpvCore.state.value
        when (kind) {
            DragKind.SEEK -> {
                if (st.duration <= 0) return
                val from = MpvCore.position.value
                ui.beginScrub(ScrubSource.SWIPE, from)
                scrubber.start(from)
                startValue = from.toFloat()
            }
            DragKind.VOLUME -> startValue = st.volume.toFloat()
            DragKind.BRIGHTNESS -> startValue = host.getBrightness()
        }
    }

    override fun onDrag(kind: DragKind, total: Offset, size: IntSize) {
        val st = MpvCore.state.value
        when (kind) {
            DragKind.SEEK -> {
                if (!scrubber.active) return
                val dxDp = total.x / density.density
                val t = (ui.scrubStart + dxDp * Prefs.swipeSeekMsPerDp.value / 1000.0).coerceIn(0.0, st.duration)
                ui.scrubTarget = t
                scrubber.update(t)
            }
            DragKind.VOLUME -> {
                val delta = -total.y / (size.height * 0.75f) * 100f
                val v = (startValue + delta).roundToInt().toDouble().coerceIn(0.0, st.volumeMax)
                MpvCore.setVolume(v)
                ui.show(if (v == 0.0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    Format.percent(v), if (v > 100) "Boosted" else null, (v / st.volumeMax).toFloat())
            }
            DragKind.BRIGHTNESS -> {
                val delta = -total.y / (size.height * 0.75f)
                val b = (startValue + delta).coerceIn(0f, 1f)
                host.setBrightness(b)
                ui.show(Icons.Rounded.BrightnessMedium, "${(b * 100).roundToInt()}%", "Brightness", b)
            }
        }
    }

    override fun onDragEnd(kind: DragKind) {
        if (kind == DragKind.SEEK && scrubber.active) {
            scrubber.end()
            ui.endScrub()
        }
        ui.feedback = null
    }

    override fun onTransform(zoom: Float, pan: Offset, size: IntSize) {
        if (ui.locked) return
        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 8f)
        panX += pan.x / (size.width * zoomScale)
        panY += pan.y / (size.height * zoomScale)
        MpvCore.setDouble("video-zoom", ln(zoomScale.toDouble()) / ln(2.0))
        MpvCore.setDouble("video-pan-x", panX.toDouble())
        MpvCore.setDouble("video-pan-y", panY.toDouble())
        ui.show(Icons.Rounded.ZoomOutMap, "${(zoomScale * 100).roundToInt()}%", "Zoom")
    }

    override fun onTransformEnd() {
        if (abs(zoomScale - 1f) < 0.04f) resetZoom()
    }

    fun resetZoom() {
        zoomScale = 1f; panX = 0f; panY = 0f
        MpvCore.setDouble("video-zoom", 0.0)
        MpvCore.setDouble("video-pan-x", 0.0)
        MpvCore.setDouble("video-pan-y", 0.0)
    }
}

@Composable
private fun ScrubOverlay(s: PlayerState, ui: PlayerUiState, modifier: Modifier) {
    val t = ui.scrubTarget ?: return
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.62f), contentColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.time(t, true, s.duration >= 3600), style = Tabular.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold))
            Text(
                Format.delta(t - ui.scrubStart) + if (s.hasVideo) "  ·  F ${Format.frameAt(t, s.fps)}" else "",
                style = Tabular.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.8f),
            )
            if (ui.scrubSource == ScrubSource.BAR) {
                val f = ui.scrubFine
                Text(
                    when (f) { 1f -> "Slide up for finer scrubbing"; 0.5f -> "Half-speed scrubbing"; 0.25f -> "Quarter-speed scrubbing"; else -> "Fine scrubbing (1/10)" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (f < 1f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedbackPill(f: Feedback?, modifier: Modifier) {
    AnimatedVisibility(f != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        val fb = f ?: return@AnimatedVisibility
        Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.66f), contentColor = Color.White) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                fb.icon?.let { Icon(it, null, Modifier.size(22.dp)) ; Spacer(Modifier.width(10.dp)) }
                Column(Modifier.widthIn(min = 64.dp)) {
                    Text(fb.text, style = Tabular.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                    fb.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f)) }
                    fb.progress?.let {
                        LinearProgressIndicator(
                            progress = { it.coerceIn(0f, 1f) },
                            modifier = Modifier.padding(top = 6.dp).width(120.dp).height(3.dp),
                            color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = 0.2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, host: PlayerHost, modifier: Modifier) {
    Surface(modifier.padding(32.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("It may be damaged, in a format that isn't supported, or no longer available.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = host::back, modifier = Modifier.padding(top = 8.dp)) { Text("Close") }
        }
    }
}

