package app.preciseplayer

import android.app.Application
import java.io.File

class PreciseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Recents.init(this)
        copyAssets()
    }

    /** mpv reads the TLS certificate bundle from a real file. */
    private fun copyAssets() {
        val out = File(filesDir, "cacert.pem")
        try {
            assets.open("cacert.pem").use { input ->
                val len = input.available().toLong()
                if (out.exists() && out.length() == len) return
                out.outputStream().use { input.copyTo(it) }
            }
        } catch (_: Exception) { }
    }
}
