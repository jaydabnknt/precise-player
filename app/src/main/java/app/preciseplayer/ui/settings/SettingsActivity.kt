package app.preciseplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.preciseplayer.FrameRowSize
import app.preciseplayer.ui.theme.Accents
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.preciseplayer.BuildConfig
import app.preciseplayer.OrientationMode
import app.preciseplayer.Prefs
import app.preciseplayer.ScrubMode
import app.preciseplayer.SeekAmount
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.theme.PreciseTheme
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

const val SOURCE_URL = "https://github.com/jaydabnknt/precise-player"
const val PRIVACY_URL = "https://github.com/jaydabnknt/precise-player/blob/main/PRIVACY.md"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startPage = intent.getStringExtra(EXTRA_PAGE)
        setContent {
            PreciseTheme {
                var page by rememberSaveable { mutableStateOf(startPage ?: PAGE_MAIN) }
                if (page == PAGE_CONTROLS) {
                    BackHandler { if (startPage == PAGE_CONTROLS) finish() else page = PAGE_MAIN }
                    ControlsEditor(onBack = { if (startPage == PAGE_CONTROLS) finish() else page = PAGE_MAIN })
                } else {
                    SettingsScreen(onBack = ::finish, onCustomize = { page = PAGE_CONTROLS })
                }
            }
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_MAIN = "main"
        const val PAGE_CONTROLS = "controls"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCustomize: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    fun open(url: String) = try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    val engineNote = "Changes apply the next time a video is opened."

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item { Header("Controls") }
            item { Choice("Customize controls", "Add, remove, reorder and move buttons (top, centre, bottom)") { onCustomize() } }
            item { Toggle("Frame-step row", "Previous / next frame buttons and the jog strip under the seek bar", Prefs.showFrameRow) }
            item {
                val labels = mapOf(FrameRowSize.AUTO to "Large in portrait, compact in landscape",
                    FrameRowSize.NORMAL to "Always compact", FrameRowSize.LARGE to "Always large")
                Choice("Frame-step row size", labels[Prefs.frameRowSize.value]!!) {
                    dialog = { OptionsDialog("Frame-step row size", FrameRowSize.values().toList(), Prefs.frameRowSize.value, { labels[it]!! }, { Prefs.frameRowSize.value = it }) { dialog = null } }
                }
            }
            item { Toggle("Show frame jog strip", "Drag sideways to step frame by frame; other controls hide while you drag", Prefs.showJog) }
            item {
                SliderItem("Jog sensitivity", "${Prefs.jogTickDp.value} dp of drag per frame",
                    Prefs.jogTickDp.value.toFloat(), 4f..40f) { Prefs.jogTickDp.value = it.roundToInt() }
            }
            item {
                SliderItem("Frames per step button press", "${Prefs.frameStepCount.value}",
                    Prefs.frameStepCount.value.toFloat(), 1f..30f) { Prefs.frameStepCount.value = it.roundToInt() }
            }
            item {
                val d = Prefs.hideDelayMs.value
                SliderItem("Hide controls after", if (d == 0) "Never (tap to hide)" else String.format(Locale.US, "%.1f s", d / 1000.0),
                    d.toFloat(), 0f..10000f) { Prefs.hideDelayMs.value = (it / 500).roundToInt() * 500 }
            }
            item {
                SliderItem("Background shade behind controls", "${(Prefs.controlsDim.value * 100).roundToInt()}%",
                    Prefs.controlsDim.value, 0f..1f) { Prefs.controlsDim.value = (it * 20).roundToInt() / 20f }
            }
            item { Toggle("Large seek bar", "Thicker bar and bigger touch area", Prefs.largeSeekBar) }
            item { Toggle("Fine scrubbing", "Slide your finger up while dragging the seek bar for ½, ¼ and 1/10 speed", Prefs.fineScrub) }
            item { Toggle("Big time display while scrubbing", null, Prefs.showScrubOverlay) }
            item { Toggle("Show remaining time", "You can also tap the time in the player to switch", Prefs.showRemaining) }
            item { Toggle("Show video title", null, Prefs.showTitle) }

            item { Header("Gestures") }
            item { Toggle("Swipe sideways to seek", null, Prefs.gestureSeek) }
            item { Toggle("Swipe up/down on the right for volume", null, Prefs.gestureVolume) }
            item { Toggle("Swipe up/down on the left for brightness", null, Prefs.gestureBrightness) }
            item { Toggle("Double-tap sides to seek", null, Prefs.gestureDoubleTapSeek) }
            item { Toggle("Double-tap middle to play / pause", null, Prefs.gestureDoubleTapPause) }
            item { Toggle("Pinch to zoom", null, Prefs.gesturePinch) }
            item { Toggle("Press and hold for fast playback", null, Prefs.gestureLongPress) }

            item { Header("Seeking") }
            item {
                Choice("Double-tap to seek", Prefs.doubleTapSeek.value.label()) {
                    dialog = { AmountDialog("Double-tap seek amount", Prefs.doubleTapSeek.value, { Prefs.doubleTapSeek.value = it }) { dialog = null } }
                }
            }
            item {
                Choice("Rewind / fast-forward buttons", Prefs.buttonSeek.value.label()) {
                    dialog = { AmountDialog("Button seek amount", Prefs.buttonSeek.value, { Prefs.buttonSeek.value = it }) { dialog = null } }
                }
            }
            item {
                val labels = mapOf(
                    ScrubMode.SMART to "Smart — fast while moving, exact frame when you slow down",
                    ScrubMode.EXACT to "Always exact frame (can feel slower on long videos)",
                    ScrubMode.KEYFRAMES to "Keyframes only (fastest, less precise)",
                )
                Choice("Scrubbing preview", labels[Prefs.scrubMode.value]!!.substringBefore(" —").substringBefore(" (")) {
                    dialog = { OptionsDialog("Scrubbing preview", ScrubMode.values().toList(), Prefs.scrubMode.value, { labels[it]!! }, { Prefs.scrubMode.value = it }) { dialog = null } }
                }
            }
            item {
                SliderItem("Swipe-to-seek speed", "${Prefs.swipeSeekMsPerDp.value * 100 / 1000} s per 100 dp of swipe",
                    Prefs.swipeSeekMsPerDp.value.toFloat(), 10f..500f) { Prefs.swipeSeekMsPerDp.value = (it / 10).roundToInt() * 10 }
            }
            item { Toggle("Strict frame stepping", "Turn on only if stepping skips frames (e.g. with deinterlacing). Makes seeking slower. $engineNote", Prefs.accurateFrameSteps) }
            item { Toggle("Show milliseconds", null, Prefs.showMilliseconds) }
            item { Toggle("Show frame number", null, Prefs.showFrameNumber) }
            item {
                Choice("Frame rate when unknown", Format.fps(Prefs.defaultFps.value.toDouble())) {
                    dialog = { FpsDialog { dialog = null } }
                }
            }

            item { Header("Playback") }
            item { Toggle("Resume where you left off", null, Prefs.resume) }
            item { Toggle("Remember playback speed", "Start new videos at the last speed you chose", Prefs.rememberSpeed) }
            item {
                SliderItem("Press-and-hold speed", Format.speed(Prefs.longPressSpeed.value.toDouble()),
                    Prefs.longPressSpeed.value, 1.25f..4f) { Prefs.longPressSpeed.value = (it * 4).roundToInt() / 4f }
            }
            item { Toggle("Picture-in-picture on leave", "Keep playing in a small window when you go home", Prefs.autoPip) }
            item {
                val labels = mapOf(
                    OrientationMode.AUTO to "Match the video", OrientationMode.SENSOR to "Follow the phone",
                    OrientationMode.LANDSCAPE to "Always landscape", OrientationMode.PORTRAIT to "Always portrait",
                )
                Choice("Screen orientation", labels[Prefs.orientation.value]!!) {
                    dialog = { OptionsDialog("Screen orientation", OrientationMode.values().toList(), Prefs.orientation.value, { labels[it]!! }, { Prefs.orientation.value = it }) { dialog = null } }
                }
            }
            item { Toggle("Pause when headphones disconnect", null, Prefs.pauseOnHeadphones) }

            item { Header("Volume") }
            item { Toggle("Volume buttons control the player", "Uses the player's own volume, separate from the phone's", Prefs.volumeKeysControlPlayer) }
            item {
                SliderItem("Volume button step", String.format(Locale.US, "%.1f%%", Prefs.volumeStep.value).replace(".0%", "%"),
                    Prefs.volumeStep.value, 0.5f..10f) { Prefs.volumeStep.value = (it * 2).roundToInt() / 2f }
            }
            item {
                SliderItem("Maximum volume", "${Prefs.volumeMax.value}% — above 100% boosts quiet audio",
                    Prefs.volumeMax.value.toFloat(), 100f..1000f) {
                    val v = (it / 25).roundToInt() * 25
                    Prefs.volumeMax.value = v
                    MpvCore.setVolumeMax(v)
                }
            }
            item { Toggle("Remember volume", null, Prefs.rememberVolume) }

            item { Header("Video") }
            item { Toggle("Hardware decoding", "Faster and uses less battery. Turn off if a video shows glitches. $engineNote", Prefs.hardwareDecoding) }
            item { Toggle("Next-generation renderer (gpu-next)", "Better quality on recent phones. $engineNote", Prefs.gpuNext) }
            item { Toggle("Debanding", "Smooths colour banding in gradients. $engineNote", Prefs.deband) }

            item { Header("Look") }
            item { AccentPicker() }
            if (Build.VERSION.SDK_INT >= 31) item { Toggle("Use wallpaper colours", "Material You colours instead of the accent above", Prefs.dynamicColor) }

            item { Header("Advanced") }
            item {
                Choice("Edit mpv.conf", "Custom options for the mpv engine") {
                    dialog = { ConfigDialog(File(ctx.filesDir, "mpv/mpv.conf")) { dialog = null } }
                }
            }

            item { Header("About") }
            item { Choice("Version", "${BuildConfig.VERSION_NAME} · playback by mpv and FFmpeg") {} }
            item { Choice("Source code", "Free software under the GNU GPL v3") { open(SOURCE_URL) } }
            item { Choice("Privacy policy", "No ads, no tracking, no data collected") { open(PRIVACY_URL) } }
            item {
                Choice("Open-source licences", "mpv, FFmpeg, libass, mpv-android and more") {
                    dialog = { LicensesDialog { dialog = null } }
                }
            }
        }
    }
    dialog?.invoke()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Accents.forEachIndexed { i, (name, color) ->
                val selected = Prefs.accent.value == i
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(color)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { Prefs.accent.value = i },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, name, tint = Color(0xFF1A1A1A)) }
            }
        }
    }
}

private val itemColors @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, sub: String?, pref: Prefs.BoolPref) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { s -> @Composable { Text(s) } },
        trailingContent = { Switch(checked = pref.value, onCheckedChange = null) },
        modifier = Modifier.clickable { pref.value = !pref.value }.padding(horizontal = 8.dp),
        colors = itemColors,
    )
}

@Composable
private fun Choice(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = itemColors,
    )
}

@Composable
private fun SliderItem(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var v by remember(current) { mutableFloatStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = v.coerceIn(range), onValueChange = { v = it; onChange(it) }, valueRange = range)
    }
}

@Composable
private fun <T> OptionsDialog(title: String, options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(o); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = o == current, onClick = null)
                        Spacer(Modifier.padding(start = 12.dp))
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountDialog(title: String, current: SeekAmount, onSave: (SeekAmount) -> Unit, onDismiss: () -> Unit) {
    var frames by remember { mutableStateOf(current.frames) }
    var text by remember {
        mutableStateOf(if (current.frames) current.value.toString() else (current.value / 1000.0).toString().removeSuffix(".0"))
    }
    val parsed: Int? = if (frames) text.trim().toIntOrNull()?.takeIf { it in 1..100000 }
                       else text.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 1000).roundToInt() }?.takeIf { it in 1..36_000_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!frames, { frames = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Seconds") }
                    SegmentedButton(frames, { frames = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Frames") }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    text, { text = it }, singleLine = true, isError = parsed == null,
                    label = { Text(if (frames) "Frames" else "Seconds (e.g. 0.5 or 0.001)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Precise down to 1 millisecond or 1 frame.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onSave(SeekAmount(parsed!!, frames)); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FpsDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(Prefs.defaultFps.value.toString().removeSuffix(".0")) }
    val v = text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it in 1f..1000f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frame rate when unknown") },
        text = {
            Column {
                Text("Used for frame stepping and frame numbers when a file doesn't say its frame rate.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, isError = v == null, label = { Text("Frames per second") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = v != null, onClick = { Prefs.defaultFps.value = v!!; onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfigDialog(file: File, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(try { file.readText() } catch (_: Exception) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("mpv.conf") },
        text = {
            Column {
                Text("One option per line, e.g. sub-font-size=48. Applies to the next video you open.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try { file.parentFile?.mkdirs(); file.writeText(text) } catch (_: Exception) {}
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val LICENSES = listOf(
    "mpv" to "GPL-2.0-or-later — mpv.io",
    "FFmpeg" to "GPL — ffmpeg.org",
    "mpv-android (build scripts, JNI)" to "MIT — github.com/mpv-android/mpv-android",
    "libass, FriBidi, HarfBuzz, FreeType, fontconfig" to "ISC / LGPL / MIT / FTL",
    "dav1d" to "BSD-2-Clause — VideoLAN",
    "libplacebo" to "LGPL-2.1-or-later",
    "Mbed TLS, curl, libxml2, Lua" to "Apache-2.0 / curl / MIT / MIT",
    "AndroidX, Jetpack Compose, Material icons" to "Apache-2.0 — Google",
    "Kotlin" to "Apache-2.0 — JetBrains",
)

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Precise Player is free software under the GNU General Public License v3. It is built on:",
                    style = MaterialTheme.typography.bodyMedium)
                LICENSES.forEach { (name, lic) ->
                    Spacer(Modifier.height(10.dp))
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("Full licence texts and source: $SOURCE_URL", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
