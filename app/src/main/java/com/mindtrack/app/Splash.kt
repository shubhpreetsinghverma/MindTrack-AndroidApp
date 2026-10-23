package com.mindtrack.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.random.Random

private val Night = Color(0xFF12121C)
private val NightText = Color(0xFFEAE7F5)
private val NightMute = Color(0xFF9A97B4)
private val MoonGlow = Color(0xFFF2E6B8)
private val StarLavender = Color(0xFFB8A5FF)

private const val WORDMARK = "MindTrack"

private data class PixelStar(val x: Float, val y: Float, val big: Boolean, val phase: Int, val lavender: Boolean)

/** How far a stage has got, from 0 to 1, between [start] and [start] + [length] of the whole timeline. */
internal fun stage(progress: Float, start: Float, length: Float): Float = ((progress - start) / length).coerceIn(0f, 1f)

/** Rounds to a few fixed steps, so things move like sprites instead of gliding. */
internal fun stepped(value: Float, steps: Int): Float = floor(value * steps).coerceAtMost(steps.toFloat()) / steps

/** How many letters of the name are showing at this point in the timeline. */
internal fun lettersShown(progress: Float, total: Int = WORDMARK.length): Int =
    floor(stage(progress, 0.40f, 0.36f) * total + 0.0001f).toInt().coerceIn(0, total)

/**
 * The opening moment: a night sky of little pixel stars, the moon rises into place a step at a time,
 * blinks, and the name types itself out underneath. A tap skips it. With animations switched off in the
 * phone settings, it simply shows the finished picture for a moment.
 */
@Composable
internal fun MindTrackAnimatedSplash(
    firstLaunch: Boolean,
    onFinished: () -> Unit
) {
    val buildMillis = if (firstLaunch) 3000 else 1700
    val holdMillis = if (firstLaunch) 700L else 400L
    val motion = remember { android.animation.ValueAnimator.areAnimatorsEnabled() }

    val timeline = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) timeline.animateTo(1f, tween(buildMillis, easing = LinearEasing))
        delay(holdMillis)
        onFinished()
    }

    val ticker = rememberInfiniteTransition(label = "intro-ticker")
    val tick = ticker.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "tick")

    val sky = remember {
        val random = Random(11)
        List(46) { PixelStar(random.nextFloat(), random.nextFloat() * 0.8f, random.nextInt(5) == 0, random.nextInt(4), random.nextInt(3) == 0) }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinished)
    ) {
        // The sky sits behind everything, in the same box as the content.
        Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
            val p = timeline.value
            val unit = 3.dp.toPx()
            sky.forEachIndexed { i, star ->
                val appear = stage(p, 0.02f + (i % 12) * 0.018f, 0.12f)
                if (appear <= 0f) return@forEachIndexed
                // Each star flickers on its own beat, in two states only.
                val on = ((tick.value * 4 + star.phase) % 2f) < 1.4f
                val alpha = (if (on) 1f else 0.35f) * (if (star.big) 0.95f else 0.6f) * appear
                val color = (if (star.lavender) StarLavender else MoonGlow).copy(alpha = alpha)
                val at = Offset(floor(star.x * size.width / unit) * unit, floor(star.y * size.height / unit) * unit)
                val side = if (star.big) unit * 1.5f else unit
                drawRect(color, at, Size(side, side))
                if (star.big) {
                    drawRect(color, Offset(at.x - side, at.y), Size(side, side))
                    drawRect(color, Offset(at.x + side, at.y), Size(side, side))
                    drawRect(color, Offset(at.x, at.y - side), Size(side, side))
                    drawRect(color, Offset(at.x, at.y + side), Size(side, side))
                }
            }
        }

        // Moon and name, drawn above the sky.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        val p = timeline.value
        val rise = stepped(stage(p, 0.12f, 0.30f), 6)
        PixelMoon(
            size = 112.dp,
            mood = if (p > 0.82f) MoonMood.HAPPY else MoonMood.SLEEPY,
            forceNight = true,
            modifier = Modifier.graphicsLayer {
                translationY = (1f - rise) * 60.dp.toPx()
                alpha = stepped(stage(timeline.value, 0.10f, 0.22f), 4)
            }
        )
        Spacer(Modifier.height(28.dp))
        val shown = lettersShown(p)
        val typing = shown < WORDMARK.length
        val cursorOn = if (typing) true else ((tick.value * 2) % 1f) < 0.6f && p < 1f
        Text(
            WORDMARK.take(shown) + if (shown > 0 && cursorOn) "▌" else "",
            color = NightText,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
            modifier = Modifier.graphicsLayer { alpha = if (shown > 0) 1f else 0f }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            tr("YOUR SPACE TO RESET"),
            color = NightMute,
            style = PixelLabelStyle.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.graphicsLayer { alpha = stepped(stage(timeline.value, 0.80f, 0.18f), 4) }
        )
    }
}
}
