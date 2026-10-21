package app.preciseplayer.ui.player

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class DragKind { SEEK, BRIGHTNESS, VOLUME }

interface GestureCallbacks {
    fun onTap()
    /** zone: -1 left third, 0 middle, 1 right third. [repeat] is true for taps continuing a double-tap streak. */
    fun onDoubleTap(zone: Int, repeat: Boolean)
    fun onLongPressStart()
    fun onLongPressEnd()
    fun swipesEnabled(): Boolean
    fun onDragStart(kind: DragKind)
    /** [total] is the movement since the drag started, in px. */
    fun onDrag(kind: DragKind, total: Offset, size: IntSize)
    fun onDragEnd(kind: DragKind)
    fun onTransform(zoom: Float, pan: Offset, size: IntSize)
    fun onTransformEnd()
}

/**
 * All touch handling on the video in one place, so taps, double-taps, long-press,
 * swipes and pinch never fight each other.
 */
fun Modifier.playerGestures(cb: GestureCallbacks): Modifier = pointerInput(cb) {
    val slop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    val edge = 36.dp.toPx()
    var pendingTap: Job? = null
    var lastTapUp = 0L
    var lastTapPos = Offset.Zero
    var streakZone = 0
    var streakUntil = 0L

    coroutineScope {
        val scope = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val fromEdge = down.position.y < edge || down.position.y > h - edge
            var longPressed = false
            var drag: DragKind? = null
            var transforming = false
            var total = Offset.Zero

            val lpJob = scope.launch {
                delay(longPressTimeout)
                longPressed = true
                cb.onLongPressStart()
            }

            fun zoneOf(x: Float) = when { x < w / 3 -> -1; x > w * 2 / 3 -> 1; else -> 0 }

            while (true) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.count { it.pressed }

                if (!transforming && drag == null && !longPressed && pressed >= 2) {
                    lpJob.cancel()
                    pendingTap?.cancel()
                    transforming = true
                }
                if (transforming) {
                    if (pressed >= 2) {
                        val z = ev.calculateZoom()
                        val p = ev.calculatePan()
                        if (z != 1f || p != Offset.Zero) cb.onTransform(z, p, size)
                    }
                    ev.changes.forEach { it.consume() }
                    if (pressed == 0) { cb.onTransformEnd(); break }
                    continue
                }

                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    lpJob.cancel()
                    when {
                        longPressed -> cb.onLongPressEnd()
                        drag != null -> cb.onDragEnd(drag!!)
                        else -> {
                            // a tap
                            val now = SystemClock.uptimeMillis()
                            val zone = zoneOf(c.position.x)
                            if (now < streakUntil && zone == streakZone && zone != 0) {
                                pendingTap?.cancel()
                                streakUntil = now + 650
                                cb.onDoubleTap(zone, true)
                            } else if (pendingTap?.isActive == true && now - lastTapUp < doubleTapTimeout &&
                                (c.position - lastTapPos).getDistance() < 120.dp.toPx()) {
                                pendingTap?.cancel()
                                if (zone != 0) { streakZone = zone; streakUntil = now + 650 }
                                cb.onDoubleTap(zone, false)
                            } else {
                                lastTapUp = now
                                lastTapPos = c.position
                                pendingTap = scope.launch {
                                    delay(doubleTapTimeout.toLong())
                                    cb.onTap()
                                }
                            }
                        }
                    }
                    break
                }

                total += c.position - c.previousPosition
                if (drag == null && !longPressed && total.getDistance() > slop) {
                    lpJob.cancel()
                    if (!fromEdge && cb.swipesEnabled()) {
                        drag = when {
                            abs(total.x) > abs(total.y) -> DragKind.SEEK
                            down.position.x < w / 2 -> DragKind.BRIGHTNESS
                            else -> DragKind.VOLUME
                        }
                        pendingTap?.cancel()
                        cb.onDragStart(drag!!)
                    } else {
                        // movement that isn't a gesture: just swallow it (no tap either)
                        drag = null
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.none { it.pressed }) break
                        }
                        break
                    }
                }
                if (drag != null) {
                    cb.onDrag(drag!!, total, size)
                    c.consume()
                }
            }
        }
    }
}
