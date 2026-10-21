package app.preciseplayer.ui.player

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.preciseplayer.Ctl
import app.preciseplayer.FrameRowSize
import app.preciseplayer.Prefs
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.mpv.PlayerState
import app.preciseplayer.mpv.Scrubber
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.theme.Tabular
import app.preciseplayer.ui.theme.TimeStyle

/** Icon for a control (also used by the layout editor). */
fun ctlIcon(c: Ctl, s: PlayerState? = null): ImageVector = when (c) {
    Ctl.PLAY_PAUSE -> if (s == null || s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
    Ctl.SEEK_BACK -> Icons.Rounded.FastRewind
    Ctl.SEEK_FWD -> Icons.Rounded.FastForward
    Ctl.FRAME_BACK -> Icons.Rounded.ChevronLeft
    Ctl.FRAME_FWD -> Icons.Rounded.ChevronRight
    Ctl.SAVE_FRAME -> Icons.Rounded.PhotoCamera
    Ctl.AUDIO -> Icons.Rounded.Audiotrack
    Ctl.SUBS -> Icons.Rounded.ClosedCaption
    Ctl.SPEED -> Icons.Rounded.Speed
    Ctl.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    Ctl.MUTE -> if (s?.mute == true) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    Ctl.GO_TO -> Icons.Rounded.Timer
    Ctl.AB_LOOP -> Icons.Rounded.Repeat
    Ctl.VIDEO -> Icons.Rounded.Tune
    Ctl.FILL -> Icons.Rounded.AspectRatio
    Ctl.ROTATE -> Icons.Rounded.ScreenRotation
    Ctl.LOCK -> Icons.Rounded.Lock
    Ctl.PIP -> Icons.Rounded.PictureInPictureAlt
    Ctl.STATS -> Icons.Rounded.Analytics
    Ctl.SETTINGS -> Icons.Rounded.Settings
}

/** What tapping a control does. */
fun runCtl(c: Ctl, ui: PlayerUiState, host: PlayerHost) {
    val s = MpvCore.state.value
    ui.poke()
    when (c) {
        Ctl.PLAY_PAUSE -> MpvCore.togglePause()
        Ctl.SEEK_BACK -> MpvCore.seekRelative(-Prefs.buttonSeek.value.seconds(s.fps))
        Ctl.SEEK_FWD -> MpvCore.seekRelative(Prefs.buttonSeek.value.seconds(s.fps))
        Ctl.FRAME_BACK -> MpvCore.step(-Prefs.frameStepCount.value)
        Ctl.FRAME_FWD -> MpvCore.step(Prefs.frameStepCount.value)
        Ctl.SAVE_FRAME -> host.screenshot()
        Ctl.AUDIO -> ui.sheet = Sheet.AUDIO
        Ctl.SUBS -> ui.sheet = Sheet.SUBS
        Ctl.SPEED -> ui.sheet = Sheet.SPEED
        Ctl.VOLUME -> ui.sheet = Sheet.VOLUME
        Ctl.MUTE -> MpvCore.setMute(!s.mute)
        Ctl.GO_TO -> ui.goToOpen = true
        Ctl.AB_LOOP -> {
            MpvCore.setAbLoop()
            ui.show(Icons.Rounded.Repeat, when {
                s.abLoopA == null -> "Loop start set"
                s.abLoopB == null -> "Looping A-B"
                else -> "Loop cleared"
            })
        }
        Ctl.VIDEO -> ui.sheet = Sheet.VIDEO
        Ctl.FILL -> ui.changeFill(!ui.fill)
        Ctl.ROTATE -> host.rotate()
        Ctl.LOCK -> ui.locked = true
        Ctl.PIP -> host.enterPip()
        Ctl.STATS -> MpvCore.toggleStats()
        Ctl.SETTINGS -> host.openSettings()
    }
}

@Composable
private fun CtlButton(c: Ctl, s: PlayerState, ui: PlayerUiState, host: PlayerHost, center: Boolean = false) {
    val white = Color.White
    val accent = MaterialTheme.colorScheme.primary
    val btn: Dp = if (center) 52.dp else 44.dp
    val ic: Dp = if (center) 28.dp else 24.dp
    when (c) {
        Ctl.PLAY_PAUSE -> {
            val big = if (center) 72.dp else 44.dp
            FilledIconButton(
                onClick = { runCtl(c, ui, host) }, modifier = Modifier.size(big),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = white.copy(alpha = if (center) 0.16f else 0.10f), contentColor = white),
            ) {
                Icon(if (s.eof && s.paused) Icons.Rounded.Replay else ctlIcon(c, s), if (s.paused) "Play" else "Pause",
                    Modifier.size(if (center) 40.dp else 26.dp))
            }
        }
        Ctl.FRAME_BACK, Ctl.FRAME_FWD -> RepeatButton({ runCtl(c, ui, host) }, Modifier.size(btn)) {
            Icon(ctlIcon(c), c.label, tint = white, modifier = Modifier.size(ic + 6.dp))
        }
        Ctl.SPEED -> TextButton(onClick = { runCtl(c, ui, host) }) {
            Text(Format.speed(s.speed), color = if (s.speed != 1.0) accent else white, style = Tabular)
        }
        Ctl.VOLUME -> TextButton(onClick = { runCtl(c, ui, host) }) {
            Icon(if (s.mute || s.volume == 0.0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null,
                tint = if (s.volume > 100) accent else white, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(Format.percent(s.volume), color = white, style = Tabular)
        }
        else -> {
            val active = when (c) {
                Ctl.SUBS -> s.tracks.any { it.type == "sub" && it.selected }
                Ctl.AB_LOOP -> s.abLoopA != null
                Ctl.MUTE -> s.mute
                Ctl.FILL -> ui.fill
                else -> false
            }
            if (center) {
                FilledIconButton(
                    onClick = { runCtl(c, ui, host) }, modifier = Modifier.size(btn),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = white.copy(alpha = 0.10f), contentColor = if (active) accent else white),
                ) { Icon(ctlIcon(c, s), c.label, Modifier.size(ic)) }
            } else {
                IconButton(onClick = { runCtl(c, ui, host) }, modifier = Modifier.size(btn)) {
                    Icon(ctlIcon(c, s), c.label, tint = if (active) accent else white, modifier = Modifier.size(ic))
                }
            }
        }
    }
}

@Composable
fun Controls(s: PlayerState, ui: PlayerUiState, host: PlayerHost, scrubber: Scrubber, onResetZoom: () -> Unit) {
    val white = Color.White
    val dim = Prefs.controlsDim.value
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // while the jog strip is dragged, everything except it and the time/frame readout fades away
    val fade by animateFloatAsState(if (ui.jogging) 0f else 1f, label = "jogfade")
    val others = Modifier.alpha(fade)
    Box(Modifier.fillMaxSize()) {
        if (dim > 0f) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp).then(others)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f * dim), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (portrait) 300.dp else 220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = dim)))))
        }

        // top bar: back, title, chosen buttons, then "more" (always there, so nothing is ever unreachable)
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().then(others)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = host::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = white) }
            if (Prefs.showTitle.value) {
                Text(s.title, color = white, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            } else Spacer(Modifier.weight(1f))
            Prefs.topButtons.value.forEach { CtlButton(it, s, ui, host) }
            IconButton(onClick = { ui.sheet = Sheet.MORE; ui.poke() }) { Icon(Icons.Rounded.MoreVert, "More", tint = white) }
        }

        // centre
        val center = Prefs.centerButtons.value
        if (center.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.Center).then(others),
                horizontalArrangement = Arrangement.spacedBy(if (center.size > 3) 16.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { center.forEach { CtlButton(it, s, ui, host, center = true) } }
        }

        // bottom
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp).padding(bottom = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeReadout(s, ui)
                Spacer(Modifier.weight(1f))
                FrameReadout(s, ui)
            }
            PreciseSeekBar(s.duration, s.abLoopA, s.abLoopB, s.cacheSeconds, ui, scrubber, large = Prefs.largeSeekBar.value, modifier = others)

            val bottom = Prefs.bottomButtons.value
            val frameRow = Prefs.showFrameRow.value
            val large = frameRow && when (Prefs.frameRowSize.value) {
                FrameRowSize.LARGE -> true
                FrameRowSize.NORMAL -> false
                FrameRowSize.AUTO -> portrait
            }
            if (large) {
                LargeFrameRow(ui, others)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).then(others), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (s.zoom != 0.0) ZoomReset(onResetZoom, ui)
                    bottom.forEach { CtlButton(it, s, ui, host) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (frameRow) {
                        Box(others) { CtlButton(Ctl.FRAME_BACK, s, ui, host) }
                        if (Prefs.showJog.value) {
                            JogStrip(onSteps = { MpvCore.step(it) }, onInteract = ui::poke,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp), height = 40.dp,
                                onActive = { ui.jogging = it })
                        } else Spacer(Modifier.weight(1f))
                        Box(others) { CtlButton(Ctl.FRAME_FWD, s, ui, host) }
                        Spacer(Modifier.width(4.dp))
                    } else Spacer(Modifier.weight(1f))
                    Row(others, verticalAlignment = Alignment.CenterVertically) {
                        if (s.zoom != 0.0) ZoomReset(onResetZoom, ui)
                        bottom.forEach { CtlButton(it, s, ui, host) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomReset(onResetZoom: () -> Unit, ui: PlayerUiState) {
    IconButton(onClick = { onResetZoom(); ui.poke() }) { Icon(Icons.Rounded.ZoomOutMap, "Reset zoom", tint = MaterialTheme.colorScheme.primary) }
}

/** Big frame-step controls: large buttons either side of a tall jog strip. */
@Composable
private fun LargeFrameRow(ui: PlayerUiState, others: Modifier) {
    val n = Prefs.frameStepCount.value
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(others) { BigStepButton(Icons.Rounded.ChevronLeft, "−$n", "Previous frame") { MpvCore.step(-n); ui.poke() } }
        if (Prefs.showJog.value) {
            JogStrip(onSteps = { MpvCore.step(it) }, onInteract = ui::poke,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp), height = 68.dp,
                onActive = { ui.jogging = it })
        } else Spacer(Modifier.weight(1f))
        Box(others) { BigStepButton(Icons.Rounded.ChevronRight, "+$n", "Next frame") { MpvCore.step(n); ui.poke() } }
    }
}

@Composable
private fun BigStepButton(icon: ImageVector, label: String, desc: String, onFire: () -> Unit) {
    RepeatButton(onFire, Modifier.size(width = 84.dp, height = 68.dp), shape = RoundedCornerShape(20.dp),
        background = Color.White.copy(alpha = 0.12f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(34.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), style = Tabular.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun TimeReadout(s: PlayerState, ui: PlayerUiState) {
    val pos by MpvCore.position.collectAsState()
    val shown = ui.scrubTarget ?: pos
    val ms = Prefs.showMilliseconds.value
    val hours = s.duration >= 3600
    val remaining = Prefs.showRemaining.value
    val end = if (remaining) "−" + Format.time((s.duration - shown).coerceAtLeast(0.0), ms, hours) else Format.time(s.duration, ms, hours)
    Text(
        Format.time(shown, ms, hours) + "  /  " + end,
        color = Color.White, style = TimeStyle,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { Prefs.showRemaining.value = !remaining; ui.poke() }.padding(vertical = 4.dp),
    )
}

@Composable
private fun FrameReadout(s: PlayerState, ui: PlayerUiState) {
    if (!Prefs.showFrameNumber.value || !s.hasVideo) return
    val pos by MpvCore.position.collectAsState()
    val n = Format.frameAt(ui.scrubTarget ?: pos, s.fps)
    Text(
        "F $n · " + Format.fps(s.fps) + if (s.fpsKnown) "" else " (assumed)",
        color = Color.White.copy(alpha = 0.75f), style = TimeStyle,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { ui.goToOpen = true }.padding(vertical = 4.dp),
    )
}
