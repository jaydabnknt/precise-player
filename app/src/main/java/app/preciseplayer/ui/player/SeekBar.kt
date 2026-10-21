package app.preciseplayer.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import app.preciseplayer.Prefs
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.mpv.Scrubber
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Seek bar with fine scrubbing: drag along it normally, or slide your finger up while
 * dragging to move at ½, ¼ or 1/10 speed for frame-accurate positioning.
 */
@Composable
fun PreciseSeekBar(
    duration: Double,
    abA: Double?,
    abB: Double?,
    cacheAhead: Double,
    ui: PlayerUiState,
    scrubber: Scrubber,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val pos by MpvCore.position.collectAsState()
    val shown = ui.scrubTarget ?: pos
    val dur by rememberUpdatedState(duration)
    val density = LocalDensity.current
    val active = ui.scrubTarget != null && ui.scrubSource == ScrubSource.BAR
    val trackH by animateDpAsState(
        when { active && large -> 12.dp; active -> 8.dp; large -> 7.dp; else -> 4.dp }, label = "track")
    val primary = MaterialTheme.colorScheme.primary
    val fineSteps = with(density) { floatArrayOf(56.dp.toPx(), 120.dp.toPx(), 190.dp.toPx()) }

    Box(
        modifier
            .fillMaxWidth()
            .height(if (large) 48.dp else 36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val d = dur
                    if (d <= 0) return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    var t = (down.position.x / w).coerceIn(0f, 1f) * d
                    ui.beginScrub(ScrubSource.BAR, MpvCore.position.value)
                    scrubber.start(MpvCore.position.value)
                    ui.scrubTarget = t
                    scrubber.update(t)
                    var lastX = down.position.x
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        val dx = c.position.x - lastX
                        lastX = c.position.x
                        val above = (-c.position.y).coerceAtLeast(0f)
                        val fine = when {
                            !Prefs.fineScrub.value -> 1f
                            above < fineSteps[0] -> 1f
                            above < fineSteps[1] -> 0.5f
                            above < fineSteps[2] -> 0.25f
                            else -> 0.1f
                        }
                        ui.scrubFine = fine
                        if (dx != 0f) {
                            t = (t + dx / w * d * fine).coerceIn(0.0, d)
                            ui.scrubTarget = t
                            scrubber.update(t)
                        }
                        c.consume()
                    }
                    scrubber.end()
                    ui.endScrub()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (large) 32.dp else 24.dp)) {
            val w = size.width
            val cy = size.height / 2
            val th = trackH.toPx()
            val r = CornerRadius(th / 2, th / 2)
            fun x(t: Double) = if (duration > 0) (t / duration).toFloat().coerceIn(0f, 1f) * w else 0f
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, cy - th / 2), Size(w, th), r)
            if (cacheAhead > 0) {
                val x0 = x(pos); val x1 = x(pos + cacheAhead)
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x0, cy - th / 2), Size(x1 - x0, th), r)
            }
            if (abA != null) {
                val xa = x(abA); val xb = if (abB != null) x(abB) else xa
                drawRoundRect(Color(0xFF8FD3C4).copy(alpha = 0.55f), Offset(xa, cy - th), Size((xb - xa).coerceAtLeast(2.dp.toPx()), th * 2), r)
            }
            val xp = x(shown)
            drawRoundRect(primary, Offset(0f, cy - th / 2), Size(xp, th), r)
            val r0 = if (large) 9.dp.toPx() else 7.dp.toPx()
            drawCircle(primary, radius = if (active) r0 + 2.dp.toPx() else r0, center = Offset(xp, cy))
        }
    }
}

/**
 * Jog strip: drag sideways to step frame by frame (one tick = one frame).
 */
@Composable
fun JogStrip(
    onSteps: (Int) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    onActive: (Boolean) -> Unit = {},
) {
    val active by rememberUpdatedState(onActive)
    var offset by remember { mutableFloatStateOf(0f) }
    val tickPx = with(LocalDensity.current) { Prefs.jogTickDp.value.coerceIn(4, 60).dp.toPx() }
    val step by rememberUpdatedState(onSteps)
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(tickPx) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f; active(true) },
                    onDragEnd = { acc = 0f; active(false) },
                    onDragCancel = { acc = 0f; active(false) },
                ) { change, dx ->
                    change.consume()
                    onInteract()
                    offset += dx
                    acc += dx
                    val n = (acc / tickPx).toInt()
                    if (n != 0) {
                        acc -= n * tickPx
                        step(n)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val shift = ((offset % tickPx) + tickPx) % tickPx
        val count = (w / tickPx).toInt() + 2
        val base = Math.floorDiv(offset.toInt(), tickPx.toInt().coerceAtLeast(1))
        for (i in -1..count) {
            val x = i * tickPx + shift
            val idx = i - base
            val major = Math.floorMod(idx, 5) == 0
            val fade = (1f - abs(x - cx) / (w / 2)).coerceIn(0f, 1f)
            val len = if (major) h * 0.5f else h * 0.28f
            drawLine(
                Color.White.copy(alpha = 0.15f + 0.6f * fade),
                Offset(x, (h - len) / 2), Offset(x, (h + len) / 2),
                strokeWidth = if (major) 2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        drawLine(primary, Offset(cx, h * 0.15f), Offset(cx, h * 0.85f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A round button that fires once on press and keeps firing while held. */
@Composable
fun RepeatButton(
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val fire by rememberUpdatedState(onFire)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) Color.White.copy(alpha = 0.24f) else background)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    fire()
                    coroutineScope {
                        val job = launch {
                            delay(380)
                            while (isActive) { fire(); delay(70) }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
