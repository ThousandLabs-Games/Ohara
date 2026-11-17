package com.thousandlabs.ohara.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.thousandlabs.ohara.data.Prefs

/** Couleurs d'accent proposées dans les réglages. */
val ACCENTS = listOf(
    "Vert Ohara" to Color(0xFF1E8E6E),
    "Bleu océan" to Color(0xFF2F6FAE),
    "Bleu stèle" to Color(0xFF34558B),
    "Ambre" to Color(0xFFB7791F),
    "Bordeaux" to Color(0xFF9E3346),
    "Violet" to Color(0xFF6E4BA6)
)

val THEMES = listOf("Selon le téléphone", "Clair", "Sombre", "Noir (écran OLED)")

private fun light(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f).compositeOver(Color(0xFFFBFAF7)),
    onPrimaryContainer = Color(0xFF10261F),
    secondary = Color(0xFF5B6B66),
    secondaryContainer = Color(0xFFE6ECE9),
    onSecondaryContainer = Color(0xFF1B2421),
    tertiary = Color(0xFF34558B),
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFBFAF7),
    surfaceVariant = Color(0xFFEDEBE5),
    surfaceContainer = Color(0xFFF2F0EB),
    surfaceContainerHigh = Color(0xFFECEAE4),
    surfaceContainerLow = Color(0xFFF6F4F0),
    onSurface = Color(0xFF1B1C1A),
    onSurfaceVariant = Color(0xFF55595A),
    outline = Color(0xFFB9B8B2),
    outlineVariant = Color(0xFFDAD8D1),
    error = Color(0xFFB3261E)
)

private fun dark(accent: Color, black: Boolean) = darkColorScheme(
    primary = accent.copy(alpha = 1f).lighten(0.25f),
    onPrimary = Color(0xFF0B1512),
    primaryContainer = accent.copy(alpha = 0.32f).compositeOver(if (black) Color.Black else Color(0xFF141615)),
    onPrimaryContainer = Color(0xFFDDF1EA),
    secondary = Color(0xFFAFC0BA),
    secondaryContainer = Color(0xFF2A3330),
    onSecondaryContainer = Color(0xFFDCE6E2),
    tertiary = Color(0xFF9DB6E4),
    background = if (black) Color.Black else Color(0xFF141615),
    surface = if (black) Color.Black else Color(0xFF141615),
    surfaceVariant = if (black) Color(0xFF151716) else Color(0xFF242725),
    surfaceContainer = if (black) Color(0xFF0E0F0F) else Color(0xFF1C1F1D),
    surfaceContainerHigh = if (black) Color(0xFF161818) else Color(0xFF242725),
    surfaceContainerLow = if (black) Color(0xFF090A0A) else Color(0xFF181A19),
    onSurface = Color(0xFFE4E3DF),
    onSurfaceVariant = Color(0xFFB4B6B3),
    outline = Color(0xFF6E716F),
    outlineVariant = Color(0xFF353837),
    error = Color(0xFFF2B8B5)
)

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

private val typography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
    )
}

val SerifTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)

@Composable
fun isDarkUi(): Boolean {
    val v by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") v
    return when (Prefs.appTheme) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
}

@Composable
fun OharaTheme(content: @Composable () -> Unit) {
    val v by Prefs.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION") v
    val accent = ACCENTS.getOrElse(Prefs.accent) { ACCENTS[0] }.second
    val darkUi = when (Prefs.appTheme) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (darkUi) dark(accent, Prefs.appTheme == 3) else light(accent)
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
