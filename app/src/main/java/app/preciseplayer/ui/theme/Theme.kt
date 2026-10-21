package app.preciseplayer.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.preciseplayer.Prefs

val Amber = Color(0xFFFFC145)
val AmberDim = Color(0xFF6B4F00)
val Ink = Color(0xFF0F1013)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF261A00),
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = Color(0xFFFFE2A6),
    secondary = Color(0xFFD7C4A0),
    onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF2C2A26),
    onSecondaryContainer = Color(0xFFF1E0BE),
    tertiary = Color(0xFF8FD3C4),
    background = Ink,
    onBackground = Color(0xFFE7E6EA),
    surface = Ink,
    onSurface = Color(0xFFE7E6EA),
    surfaceVariant = Color(0xFF26272C),
    onSurfaceVariant = Color(0xFFC4C3CA),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF15161A),
    surfaceContainer = Color(0xFF1A1B20),
    surfaceContainerHigh = Color(0xFF212227),
    surfaceContainerHighest = Color(0xFF2A2B31),
    outline = Color(0xFF8E8D95),
    outlineVariant = Color(0xFF3A3B41),
)

/** Accent colours the user can pick in Settings. */
val Accents = listOf(
    "Amber" to Color(0xFFFFC145),
    "Teal" to Color(0xFF4FD8C4),
    "Sky" to Color(0xFF7CC4FF),
    "Violet" to Color(0xFFB69CFF),
    "Rose" to Color(0xFFFF8FB1),
    "Lime" to Color(0xFFB5E36B),
    "Coral" to Color(0xFFFF8A65),
    "White" to Color(0xFFECECEF),
)

@Composable
fun PreciseTheme(content: @Composable () -> Unit) {
    val scheme = if (Prefs.dynamicColor.value && Build.VERSION.SDK_INT >= 31)
        dynamicDarkColorScheme(LocalContext.current).copy(background = Ink, surface = Ink)
    else Accents.getOrNull(Prefs.accent.value)?.second?.let { a ->
        Scheme.copy(primary = a, onPrimary = Color(0xFF1A1A1A), primaryContainer = a.copy(alpha = 0.28f).compositeOverInk(), onPrimaryContainer = a)
    } ?: Scheme
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

/** Digits that don't jitter as they change (tabular figures). */
val Tabular = TextStyle(fontFeatureSettings = "tnum")
val TimeStyle = TextStyle(fontFeatureSettings = "tnum", fontSize = 13.sp, fontWeight = FontWeight.Medium)

private fun Color.compositeOverInk(): Color {
    val a = alpha
    return Color(red * a + Ink.red * (1 - a), green * a + Ink.green * (1 - a), blue * a + Ink.blue * (1 - a), 1f)
}
