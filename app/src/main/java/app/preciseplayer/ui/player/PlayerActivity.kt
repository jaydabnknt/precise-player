package app.preciseplayer.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.system.Os
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.preciseplayer.OrientationMode
import app.preciseplayer.Prefs
import app.preciseplayer.R
import app.preciseplayer.Recents
import app.preciseplayer.mpv.MpvCore
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.settings.SettingsActivity
import app.preciseplayer.ui.theme.PreciseTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class PlayerActivity : ComponentActivity(), PlayerHost {
    private val ui = PlayerUiState()
    private var currentUri: String? = null
    private var manualOrientation: Int? = null

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var hasFocus = false
    private var pausedByFocusLoss = false
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; pausedByFocusLoss = false; MpvCore.setPaused(true) }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (!MpvCore.state.value.paused) { pausedByFocusLoss = true; MpvCore.setPaused(true) }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        if (pausedByFocusLoss) { pausedByFocusLoss = false; MpvCore.setPaused(false) }
                    }
                }
            }
            .build()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && Prefs.pauseOnHeadphones.value) MpvCore.setPaused(true)
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_PIP, 0)) {
                PIP_PLAY_PAUSE -> MpvCore.togglePause()
                PIP_BACK -> MpvCore.seekRelative(-Prefs.buttonSeek.value.seconds(MpvCore.state.value.fps))
                PIP_FWD -> MpvCore.seekRelative(Prefs.buttonSeek.value.seconds(MpvCore.state.value.fps))
            }
        }
    }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) MpvCore.addSubtitle(resolve(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        MpvCore.create(this)
        MpvCore.setDisplayFps(ContextCompat.getDisplayOrDefault(this).refreshRate)
        handleIntent(intent)

        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent { PreciseTheme { PlayerScreen(ui, this) } }

        lifecycleScope.launch {
            MpvCore.state.map { Triple(it.paused, it.fileLoaded, it.aspect) }.distinctUntilChanged().collect { (paused, loaded, _) ->
                if (!paused) requestFocus()
                if (loaded) updatePip()
            }
        }
        lifecycleScope.launch {
            MpvCore.state.map { it.videoW to it.videoH }.distinctUntilChanged().collect { applyOrientation() }
        }
        lifecycleScope.launch {
            MpvCore.state.map { it.fileLoaded to it.title }.distinctUntilChanged().collect { (loaded, title) ->
                val uri = currentUri
                if (loaded && uri != null) Recents.touch(uri, title, duration = MpvCore.state.value.duration)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                MpvCore.state.map { it.volume }.distinctUntilChanged().collect {
                    if (Prefs.rememberVolume.value && MpvCore.state.value.created) Prefs.lastVolume.value = it.toFloat()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress()
        MpvCore.saveResumePoint()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent.data
        }
        if (uri == null) {
            if (currentUri == null) finish()
            return
        }
        currentUri = uri.toString()
        ui.controlsVisible = true
        ui.poke()
        MpvCore.loadFile(resolve(uri))
    }

    /** Turns a content:// Uri into something mpv opens fastest: a real path when readable, else the Uri itself. */
    private fun resolve(uri: Uri): String = when (uri.scheme) {
        "file" -> uri.path ?: uri.toString()
        "content" -> try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val path = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (path.startsWith("/") && File(path).canRead()) path else null
            } ?: uri.toString()
        } catch (_: Exception) { uri.toString() }
        else -> uri.toString()
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun saveProgress() {
        val uri = currentUri ?: return
        val s = MpvCore.state.value
        if (!s.fileLoaded) return
        val pos = MpvCore.position.value
        // finished (or nearly): start over next time
        val p = if (s.duration > 0 && pos > s.duration - 3) 0.0 else pos
        Recents.touch(uri, s.title, p, s.duration)
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        MpvCore.saveResumePoint()
        if (!isInPictureInPictureMode) {
            MpvCore.setPaused(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(pipReceiver) } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (isFinishing) MpvCore.destroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyOrientation() {
        val s = MpvCore.state.value
        requestedOrientation = manualOrientation ?: when (Prefs.orientation.value) {
            OrientationMode.AUTO -> when {
                !s.hasVideo -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                s.aspect >= 1.0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    // ---------------------------------------------------------------- keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = MpvCore.state.value
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!Prefs.volumeKeysControlPlayer.value) return super.onKeyDown(keyCode, event)
                val step = Prefs.volumeStep.value.toDouble()
                val v = (s.volume + if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) step else -step).coerceIn(0.0, s.volumeMax)
                MpvCore.setVolume(v)
                if (s.mute) MpvCore.setMute(false)
                ui.show(if (v == 0.0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    Format.percent(v), if (v > 100) "Boosted" else "Player volume", (v / s.volumeMax).toFloat())
                return true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.repeatCount == 0) {
                    MpvCore.togglePause()
                    ui.show(if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.paused) "Play" else "Pause")
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { MpvCore.setPaused(false); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { MpvCore.setPaused(true); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                MpvCore.seekRelative(-Prefs.buttonSeek.value.seconds(s.fps)); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                MpvCore.seekRelative(Prefs.buttonSeek.value.seconds(s.fps)); return true
            }
            KeyEvent.KEYCODE_COMMA -> { MpvCore.step(-1); return true }
            KeyEvent.KEYCODE_PERIOD -> { MpvCore.step(1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- picture-in-picture

    private fun pipParams(): PictureInPictureParams {
        val s = MpvCore.state.value
        val a = s.aspect.coerceIn(1 / 2.39, 2.39)
        val ratio = Rational((a * 10000).toInt(), 10000)
        fun action(icon: Int, title: String, code: Int) = RemoteAction(
            Icon.createWithResource(this, icon), title, title,
            PendingIntent.getBroadcast(this, code, Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP, code),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(listOf(
                action(R.drawable.ic_pip_back, "Back", PIP_BACK),
                if (s.paused) action(R.drawable.ic_pip_play, "Play", PIP_PLAY_PAUSE) else action(R.drawable.ic_pip_pause, "Pause", PIP_PLAY_PAUSE),
                action(R.drawable.ic_pip_fwd, "Forward", PIP_FWD),
            ))
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(Prefs.autoPip.value && !s.paused && s.fileLoaded && s.hasVideo)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePip() {
        try { setPictureInPictureParams(pipParams()) } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = MpvCore.state.value
        if (Build.VERSION.SDK_INT < 31 && Prefs.autoPip.value && !s.paused && s.hasVideo) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { ui.sheet = null; ui.goToOpen = false }
        else { ui.controlsVisible = false; hideSystemBars() }
    }

    // ---------------------------------------------------------------- PlayerHost

    override fun back() = finish()

    override fun enterPip() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Picture-in-picture isn't available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        try { enterPictureInPictureMode(pipParams()) } catch (_: Exception) {}
    }

    override fun rotate() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        manualOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        applyOrientation()
    }

    override fun screenshot() {
        val s = MpvCore.state.value
        val pos = MpvCore.position.value
        val file = File(cacheDir, "frame.png")
        MpvCore.screenshot(file) { ok ->
            val saved = ok && try {
                val base = s.title.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(60).ifBlank { "video" }
                val name = "${base}_${Format.time(pos, true).replace(':', '-')}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Precise Player")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(item)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(item, values, null, null)
                file.delete()
                true
            } catch (_: Exception) { false }
            runOnUiThread {
                Toast.makeText(this, if (saved) "Frame saved to Pictures/Precise Player" else "Couldn't save the frame", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun customizeControls() {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_CONTROLS))
    }

    override fun pickSubtitle() {
        try { subtitlePicker.launch(arrayOf("*/*")) } catch (_: Exception) {}
    }

    override fun getBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
    }

    override fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }

    companion object {
        private const val ACTION_PIP = "app.preciseplayer.PIP"
        private const val EXTRA_PIP = "a"
        private const val PIP_PLAY_PAUSE = 1
        private const val PIP_BACK = 2
        private const val PIP_FWD = 3

        fun open(context: Context, uri: Uri) {
            context.startActivity(Intent(context, PlayerActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
