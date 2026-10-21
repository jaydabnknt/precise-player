package app.preciseplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.preciseplayer.Prefs
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.mpv.PlayerState
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.theme.Tabular
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(s: PlayerState, ui: PlayerUiState, host: PlayerHost) {
    val sheet = ui.sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { ui.sheet = null; ui.poke() },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            when (sheet) {
                Sheet.AUDIO -> TracksSheet(s, "audio", ui, host)
                Sheet.SUBS -> TracksSheet(s, "sub", ui, host)
                Sheet.SPEED -> SpeedSheet(s)
                Sheet.VOLUME -> VolumeSheet(s)
                Sheet.VIDEO -> VideoSheet(s)
                Sheet.MORE -> MoreSheet(s, ui, host)
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = Tabular.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary) }
    }
}

fun textSlot(text: String?): (@Composable () -> Unit)? {
    if (text == null) return null
    return { Text(text) }
}

private val itemColors @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun TracksSheet(s: PlayerState, type: String, ui: PlayerUiState, host: PlayerHost) {
    val tracks = s.tracks.filter { it.type == type }
    SheetTitle(if (type == "audio") "Audio" else "Subtitles")
    if (type == "sub") {
        ListItem(
            headlineContent = { Text("Off") },
            leadingContent = { RadioButton(selected = tracks.none { it.selected }, onClick = null) },
            modifier = Modifier.clickable { MpvCore.selectTrack(type, null) },
            colors = itemColors,
        )
    }
    tracks.forEachIndexed { i, t ->
        ListItem(
            headlineContent = { Text(t.label(i)) },
            supportingContent = textSlot(if (t.external) "External file" else null),
            leadingContent = { RadioButton(selected = t.selected, onClick = null) },
            modifier = Modifier.clickable { MpvCore.selectTrack(type, t.id) },
            colors = itemColors,
        )
    }
    if (tracks.isEmpty() && type == "audio") {
        Text("This file has no audio.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (type == "sub") {
        ListItem(
            headlineContent = { Text("Add subtitle file…") },
            leadingContent = { Icon(Icons.Rounded.Add, null) },
            modifier = Modifier.clickable { ui.sheet = null; host.pickSubtitle() },
            colors = itemColors,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    val delay = if (type == "audio") s.audioDelay else s.subDelay
    val prop = if (type == "audio") "audio-delay" else "sub-delay"
    StepperRow(
        label = if (type == "audio") "Audio delay" else "Subtitle delay",
        value = String.format(Locale.US, "%+.0f ms", delay * 1000),
        onMinus = { MpvCore.setDouble(prop, delay - 0.05) },
        onPlus = { MpvCore.setDouble(prop, delay + 0.05) },
        onReset = { MpvCore.setDouble(prop, 0.0) },
        hint = "Steps of 50 ms",
    )
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, onReset: (() -> Unit)? = null, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "Less") }
        Text(value, style = Tabular.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), modifier = Modifier.width(92.dp).clickable(enabled = onReset != null) { onReset?.invoke() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "More") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSheet(s: PlayerState) {
    SheetTitle("Playback speed", Format.speed(s.speed))
    var slider by remember { mutableFloatStateOf(s.speed.toFloat()) }
    LaunchedEffect(s.speed) { slider = s.speed.toFloat() }
    Slider(
        value = slider.coerceIn(0.1f, 4f),
        onValueChange = { v -> slider = (v * 20).roundToInt() / 20f; MpvCore.setSpeed(slider.toDouble()) },
        valueRange = 0.1f..4f,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0)) {
            FilterChip(selected = s.speed == v, onClick = { MpvCore.setSpeed(v) }, label = { Text(Format.speed(v), style = Tabular) })
        }
    }
    StepperRow("Fine adjust", Format.speed(s.speed),
        onMinus = { MpvCore.setSpeed(((s.speed - 0.01) * 100).roundToInt() / 100.0) },
        onPlus = { MpvCore.setSpeed(((s.speed + 0.01) * 100).roundToInt() / 100.0) },
        onReset = { MpvCore.setSpeed(1.0) }, hint = "Steps of 0.01× · tap value to reset")
    Text(
        "Tip: press and hold the video to play at ${Format.speed(Prefs.longPressSpeed.value.toDouble())} while held.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun VolumeSheet(s: PlayerState) {
    SheetTitle("Volume", Format.percent(s.volume))
    Slider(
        value = s.volume.toFloat().coerceIn(0f, s.volumeMax.toFloat()),
        onValueChange = { MpvCore.setVolume(it.roundToInt().toDouble()) },
        valueRange = 0f..s.volumeMax.toFloat(),
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    StepperRow("Fine adjust", Format.percent(s.volume),
        onMinus = { MpvCore.setVolume(s.volume - 1) }, onPlus = { MpvCore.setVolume(s.volume + 1) },
        onReset = { MpvCore.setVolume(100.0) }, hint = "Steps of 1% · tap value for 100%")
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mute", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = s.mute, onCheckedChange = { MpvCore.setMute(it) })
    }
    Text(
        "This is the player's own volume, separate from your phone's. Above 100% boosts quiet videos " +
            "(up to ${s.volumeMax.roundToInt()}%, changeable in Settings).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoSheet(s: PlayerState) {
    SheetTitle("Video & picture")
    Text("Aspect ratio", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf("Original" to "no", "16:9" to "16:9", "4:3" to "4:3", "21:9" to "2.333", "1:1" to "1", "9:16" to "0.5625")
        for ((label, v) in options) {
            val selected = s.aspectOverride == v || (v != "no" && s.aspectOverride.toDoubleOrNull()?.let { a -> parseAspect(v)?.let { kotlin.math.abs(it - a) < 0.01 } } == true)
            FilterChip(selected = selected, onClick = { MpvCore.setString("video-aspect-override", v) }, label = { Text(label) })
        }
    }
    var fill by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Fill screen", style = MaterialTheme.typography.bodyLarge)
            Text("Crops the edges instead of showing black bars", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = fill, onCheckedChange = { fill = it; MpvCore.setDouble("panscan", if (it) 1.0 else 0.0) })
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    PictureSlider("Brightness", s.brightness, "brightness")
    PictureSlider("Contrast", s.contrast, "contrast")
    PictureSlider("Saturation", s.saturation, "saturation")
    PictureSlider("Gamma", s.gamma, "gamma")
    PictureSlider("Hue", s.hue, "hue")
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { MpvCore.resetPicture() }) { Text("Reset picture") }
    }
    Text(
        "Decoder: " + (if (s.hwdec.isBlank() || s.hwdec == "no") "software" else "hardware (${s.hwdec})") +
            (if (s.hasVideo) " · ${s.videoW}×${s.videoH}" else "") + " · " + Format.fps(s.fps),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

private fun parseAspect(v: String): Double? = if (':' in v) v.split(':').let { it[0].toDouble() / it[1].toDouble() } else v.toDoubleOrNull()

@Composable
private fun PictureSlider(label: String, value: Int, prop: String) {
    var v by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { v = value.toFloat() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Slider(
            value = v, onValueChange = { v = it.roundToInt().toFloat(); MpvCore.setInt(prop, v.toInt()) },
            valueRange = -100f..100f, modifier = Modifier.weight(1f),
        )
        Text(v.toInt().toString(), style = Tabular, modifier = Modifier.width(40.dp).clickable { MpvCore.setInt(prop, 0) },
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun MoreSheet(s: PlayerState, ui: PlayerUiState, host: PlayerHost) {
    @Composable
    fun item(icon: ImageVector, title: String, sub: String? = null, onClick: () -> Unit) = ListItem(
        headlineContent = { Text(title) },
        supportingContent = textSlot(sub),
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = itemColors,
    )
    item(Icons.Rounded.Timer, "Go to time or frame…", "Jump to an exact timestamp or frame number") { ui.sheet = null; ui.goToOpen = true }
    val ab = when {
        s.abLoopA == null -> "Tap to set the start point at the current position"
        s.abLoopB == null -> "Start ${Format.time(s.abLoopA)} · tap to set the end point"
        else -> "${Format.time(s.abLoopA)} → ${Format.time(s.abLoopB)} · tap to clear"
    }
    item(Icons.Rounded.Repeat, "A-B loop", ab) { MpvCore.setAbLoop() }
    item(Icons.Rounded.PhotoCamera, "Save frame as image", "Exact current frame, full resolution, to Pictures") { ui.sheet = null; host.screenshot() }
    item(Icons.Rounded.Tune, "Video & picture", "Aspect ratio, crop, brightness, contrast…") { ui.sheet = Sheet.VIDEO }
    item(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture") { ui.sheet = null; host.enterPip() }
    item(Icons.Rounded.Analytics, "Playback statistics", "Show or hide mpv's live stats") { ui.sheet = null; MpvCore.toggleStats() }
    item(Icons.Rounded.Settings, "Settings") { ui.sheet = null; host.openSettings() }
}

@Composable
fun GoToDialog(s: PlayerState, ui: PlayerUiState) {
    val initial = Format.time(MpvCore.position.value, true)
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun go() {
        val t = Format.parseTime(text.text, s.fps)
        if (t == null || (s.duration > 0 && t > s.duration)) { error = true; return }
        MpvCore.seekTo(t, true)
        ui.goToOpen = false
        ui.poke()
    }
    AlertDialog(
        onDismissRequest = { ui.goToOpen = false },
        title = { Text("Go to") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false },
                    singleLine = true, isError = error, textStyle = Tabular.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Examples: 1:23.456 · 83.5 · 1:02:03 · f1234 (frame number at ${Format.fps(s.fps)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = ::go) { Text("Go") } },
        dismissButton = { TextButton(onClick = { ui.goToOpen = false }) { Text("Cancel") } },
    )
}
