package com.mindtrack.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** The little moon's face. It follows how the day went, and stays calm either way. */
internal enum class MoonMood { SLEEPY, HAPPY, GENTLE }

internal fun moonMoodFor(mood: Int?): MoonMood = when {
    mood == null -> MoonMood.SLEEPY
    mood >= 4 -> MoonMood.HAPPY
    mood <= 2 -> MoonMood.GENTLE
    else -> MoonMood.SLEEPY
}

private const val GRID = 12

/** A crescent on a 12 by 12 grid: a big circle with a smaller one bitten out of it. */
internal val moonCells: List<Pair<Int, Int>> = buildList {
    for (y in 0 until GRID) for (x in 0 until GRID) {
        val outer = (x - 5.5) * (x - 5.5) + (y - 5.7) * (y - 5.7) <= 30.5
        val bite = (x - 8.2) * (x - 8.2) + (y - 4.2) * (y - 4.2) <= 24.0
        if (outer && !bite) add(x to y)
    }
}

/** Closed-eye pixels for each expression. All of them sit on the thick part of the crescent. */
internal fun moonEye(mood: MoonMood): List<Pair<Int, Int>> = when (mood) {
    MoonMood.SLEEPY -> listOf(2 to 6, 3 to 6)
    MoonMood.HAPPY -> listOf(2 to 5, 1 to 6, 3 to 6)
    MoonMood.GENTLE -> listOf(1 to 6, 2 to 6, 3 to 7)
}

/** False when the person has switched animations off in the phone's developer or accessibility settings. */
private fun animationsAllowed(): Boolean = android.animation.ValueAnimator.areAnimatorsEnabled()

@Composable
internal fun PixelMoon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    mood: MoonMood = MoonMood.SLEEPY,
    animated: Boolean = true,
    forceNight: Boolean = false
) {
    val dark = forceNight || ThemeState.isDark
    val body = if (dark) Color(0xFFF2E6B8) else Color(0xFF8E83C9)
    val ink = if (dark) Color(0xFF12121C) else Color(0xFF2A2733)
    val blush = Color(0xFFF2A6B8)
    val eye = remember(mood) { moonEye(mood) }
    val live = animated && remember { animationsAllowed() }

    val clock = rememberInfiniteTransition(label = "moon")
    val bob = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val blink = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart), label = "blink")

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // Moves in whole steps, like a sprite, rather than gliding.
                if (live) translationY = -floor(sin(bob.value * 2 * PI).toFloat().coerceAtLeast(0f) * 3f) * (this.size.height / GRID) * 0.35f
            }
    ) {
        val cell = this.size.width / GRID
        moonCells.forEach { (x, y) -> drawRect(body, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
        val eyesOpenLine = !live || blink.value < 0.94f
        if (eyesOpenLine) eye.forEach { (x, y) -> drawRect(ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
        drawRect(blush, Offset(2 * cell, 8 * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}

/** Tiny label text: wide-spaced capitals in Pixelify Sans (SIL Open Font License, see licenses/). */
internal val PixelFont = FontFamily(androidx.compose.ui.text.font.Font(R.font.pixelify_sans))

internal val PixelLabelStyle = TextStyle(
    fontFamily = PixelFont,
    fontSize = 11.sp,
    letterSpacing = 1.4.sp
)
