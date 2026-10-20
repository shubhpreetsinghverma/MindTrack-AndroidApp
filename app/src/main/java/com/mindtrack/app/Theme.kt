package com.mindtrack.app

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

/** The original premium navy and lavender. */
internal val DarkPalette = Palette(
    background = Color(0xFF090D1B),
    surface = Color(0xFF12192B),
    surfaceElevated = Color(0xFF1A2340),
    surfaceSelected = Color(0xFF272746),
    lavender = Color(0xFFB8A5FF),
    lavenderBright = Color(0xFF9B82FF),
    textPrimary = Color(0xFFF5F3FF),
    textSecondary = Color(0xFFA4AEC7),
    mint = Color(0xFF91E0C3),
    border = Color(0xFF29324A),
    coral = Color(0xFFFFA7A7),
    navSurface = Color(0xFF0E1425),
    guideGradient = listOf(Color(0xFF292447), Color(0xFF1C2440), Color(0xFF25213D)),
    guideInk = Color(0xFFD0C1FF)
)

/** White with a lavender tint. Accent colours are darker than in the dark theme so text stays readable. */
internal val LightPalette = Palette(
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFF2EFFF),
    surfaceElevated = Color(0xFFE7E2FF),
    surfaceSelected = Color(0xFFDCD4FF),
    lavender = Color(0xFF6A55D4),
    lavenderBright = Color(0xFF7A62F0),
    textPrimary = Color(0xFF1D1A3A),
    textSecondary = Color(0xFF5C6285),
    mint = Color(0xFF17876A),
    border = Color(0xFFDAD4F4),
    coral = Color(0xFFD1435B),
    navSurface = Color(0xFFF4F1FF),
    guideGradient = listOf(Color(0xFFE4DEFF), Color(0xFFEDE9FF), Color(0xFFE0D9FF)),
    guideInk = Color(0xFF5B46C4)
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
            outline = Color(0xFF8E89B3), outlineVariant = p.border
        )
    }
}

internal fun readThemeMode(prefs: android.content.SharedPreferences): ThemeMode =
    runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
