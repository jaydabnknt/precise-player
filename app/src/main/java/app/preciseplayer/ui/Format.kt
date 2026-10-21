package app.preciseplayer.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

object Format {
    /** 1:02:03.456 / 2:03.456 (or without milliseconds). */
    fun time(seconds: Double, ms: Boolean = true, forceHours: Boolean = false): String {
        if (seconds.isNaN() || seconds.isInfinite()) return "--:--"
        val neg = seconds < 0
        val totalMs = (abs(seconds) * 1000).roundToLong()
        val h = totalMs / 3_600_000
        val m = (totalMs / 60_000) % 60
        val s = (totalMs / 1000) % 60
        val milli = totalMs % 1000
        val sb = StringBuilder()
        if (neg) sb.append('-')
        if (h > 0 || forceHours) sb.append(h).append(':').append(pad2(m)) else sb.append(m)
        sb.append(':').append(pad2(s))
        if (ms) sb.append('.').append(milli.toString().padStart(3, '0'))
        return sb.toString()
    }

    /** +1.250 s, -12.000 s, +1:05.000 */
    fun delta(seconds: Double): String {
        val sign = if (seconds < 0) "−" else "+"
        val a = abs(seconds)
        return if (a < 60) sign + String.format(Locale.US, "%.3f s", a) else sign + time(a)
    }

    fun fps(fps: Double): String {
        val r = String.format(Locale.US, "%.3f", fps).trimEnd('0').trimEnd('.')
        return "$r fps"
    }

    fun speed(s: Double): String = String.format(Locale.US, "%.2f", s).trimEnd('0').trimEnd('.') + "×"

    fun percent(v: Double): String = "${v.roundToLong()}%"

    private fun pad2(v: Long) = if (v < 10) "0$v" else v.toString()

    /**
     * Parses "1:23.456", "01:02:03", "83.5", "83s", or a frame number "f1234" / "#1234".
     * Returns seconds, or null if not understood.
     */
    fun parseTime(input: String, fps: Double): Double? {
        val t = input.trim().replace(',', '.')
        if (t.isEmpty()) return null
        if (t.startsWith("f", true) || t.startsWith("#")) {
            val n = t.drop(1).trim().toLongOrNull() ?: return null
            return n / fps
        }
        val clean = t.removeSuffix("s").removeSuffix("S")
        val parts = clean.split(':')
        if (parts.size > 3) return null
        var total = 0.0
        for ((i, p) in parts.withIndex()) {
            val v = if (i == parts.lastIndex) p.toDoubleOrNull() else p.toLongOrNull()?.toDouble()
            v ?: return null
            total = total * 60 + v
        }
        return total
    }

    fun frameAt(seconds: Double, fps: Double): Long = floor(seconds * fps + 0.01).toLong()

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
        else -> "${bytes / 1024} KB"
    }
}
