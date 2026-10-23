package com.mindtrack.app

import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

internal class Palette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSelected: Color,
    val lavender: Color,
    val lavenderBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val mint: Color,
    val border: Color,
    val coral: Color,
    val navSurface: Color,
    val guideGradient: List<Color>,
    val guideInk: Color
)

/** "Night pixel": deep ink with soft lavender, like a night-light. */
internal val DarkPalette = Palette(
    background = Color(0xFF12121C),
    surface = Color(0xFF1A1A28),
    surfaceElevated = Color(0xFF222234),
    surfaceSelected = Color(0xFF2C2C42),
    lavender = Color(0xFFB8A5FF),
    lavenderBright = Color(0xFF9B82FF),
    textPrimary = Color(0xFFEAE7F5),
    textSecondary = Color(0xFF9A97B4),
    mint = Color(0xFF91E0C3),
    border = Color(0xFF2A2A40),
    coral = Color(0xFFFFA7A7),
    navSurface = Color(0xFF12121C),
    guideGradient = listOf(Color(0xFF222234), Color(0xFF222234), Color(0xFF222234)),
    guideInk = Color(0xFFD0C1FF)
)

/** "Quiet pixel": warm off-white with dusty lavender. Accent text is dark enough to read on the paper colour. */
internal val LightPalette = Palette(
    background = Color(0xFFF8F6F1),
    surface = Color(0xFFF0EDE5),
    surfaceElevated = Color(0xFFE9E5DB),
    surfaceSelected = Color(0xFFDFDACD),
    lavender = Color(0xFF6B5FB3),
    lavenderBright = Color(0xFF7D70C9),
    textPrimary = Color(0xFF2A2733),
    textSecondary = Color(0xFF6C6879),
    mint = Color(0xFF2F8062),
    border = Color(0xFFDCD8CC),
    coral = Color(0xFFB94A4A),
    navSurface = Color(0xFFF8F6F1),
    guideGradient = listOf(Color(0xFFE9E5DB), Color(0xFFE9E5DB), Color(0xFFE9E5DB)),
    guideInk = Color(0xFF6B5FB3)
)

/**
 * The current theme. Colours are read through [palette] from composables, drawing code and modifiers,
 * so changing [mode] updates everything that reads them.
 */
internal object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)

    /** Whether the phone itself is in dark mode. Set when the activity starts (it restarts if this changes). */
    var systemDark: Boolean = true

    val isDark: Boolean
        get() = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    val palette: Palette get() = if (isDark) DarkPalette else LightPalette
}

internal fun mindTrackColorScheme(): ColorScheme {
    val p = ThemeState.palette
    return if (ThemeState.isDark) {
        darkColorScheme(
            primary = p.lavender, onPrimary = p.background,
            secondary = p.mint, onSecondary = p.background,
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary
        )
    } else {
        lightColorScheme(
            primary = p.lavender, onPrimary = p.background,
            secondary = p.mint, onSecondary = p.background,
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary,
            outline = Color(0xFF8A8696), outlineVariant = p.border
        )
    }
}

internal fun readThemeMode(prefs: android.content.SharedPreferences): ThemeMode =
    runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)


/** Dialogs and sheets get small, squarer corners to match the flat look. */
internal val mindTrackShapes = androidx.compose.material3.Shapes(
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
)
