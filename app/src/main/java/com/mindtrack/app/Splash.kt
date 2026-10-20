package com.mindtrack.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SplashNight = Color(0xFF090D1B)
private val SplashText = Color(0xFFF5F3FF)
private val SplashLavender = Color(0xFFB8A5FF)
private val SplashLavenderBright = Color(0xFF9B82FF)
private val Moonlight = Color(0xFFF4E9FF)
private val DeepSky = Color(0xFF04061A)

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))
private fun window(progress: Float, start: Float, length: Float) =
    ((progress - start) / length).coerceIn(0f, 1f)

private data class SkyStar(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

private class IntroStages(progress: Float) {
    val sky = ease(window(progress, 0f, 0.25f))
    val glow = ease(window(progress, 0.05f, 0.25f))
    val orbit = window(progress, 0.15f, 0.35f)
    val moon = ease(window(progress, 0.30f, 0.34f))
    val star = window(progress, 0.55f, 0.20f)
    val waves = ease(window(progress, 0.58f, 0.24f))
    val shooting = window(progress, 0.38f, 0.22f)
    val word = window(progress, 0.68f, 0.24f)
}

/**
 * Brand intro: a night sky fades in and twinkles, the orbit draws itself, the moon
 * rises into it, a star sparkles, the waves roll in and the name settles letter by letter.
 * The full sequence plays on first launch; afterwards a shorter version plays. Tap to skip.
 *
 * Animated values are read only inside draw / graphicsLayer lambdas, so the frame loop
 * redraws without recomposing the screen.
 */
@Composable
internal fun MindTrackAnimatedSplash(
    firstLaunch: Boolean,
    onFinished: () -> Unit
) {
    val buildMillis = if (firstLaunch) 3600 else 1900
    val holdMillis = if (firstLaunch) 600L else 350L

    val timeline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        timeline.animateTo(1f, tween(buildMillis, easing = LinearEasing))
        delay(holdMillis)
        onFinished()
    }

    val ambient = rememberInfiniteTransition(label = "intro-ambient")
    val twinkle = ambient.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "twinkle"
    )
    val glowPulse = ambient.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val waveDrift = ambient.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )

    val sky = remember {
        val random = Random(7)
        List(54) {
            SkyStar(
                x = random.nextFloat(), y = random.nextFloat() * 0.85f,
                size = 0.6f + random.nextFloat() * 1.5f,
                phase = random.nextFloat() * (2 * PI).toFloat(),
                speed = 1f + random.nextInt(3)
            )
        }
    }
    val backdrop = remember { Brush.verticalGradient(listOf(DeepSky, SplashNight, SplashNight)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stages = IntroStages(timeline.value)
            val t = twinkle.value
            sky.forEach { s ->
                val tw = 0.55f + 0.45f * sin(t * s.speed + s.phase)
                drawCircle(
                    color = Moonlight.copy(alpha = (0.75f * tw * stages.sky).coerceIn(0f, 1f)),
                    radius = s.size * density,
                    center = Offset(s.x * size.width, s.y * size.height)
                )
            }
            val shooting = stages.shooting
            if (shooting > 0f && shooting < 1f) {
                val head = Offset(size.width * (0.95f - 0.6f * shooting), size.height * (0.08f + 0.22f * shooting))
                val tail = Offset(head.x + size.width * 0.14f, head.y - size.height * 0.04f)
                val fade = sin(shooting * PI.toFloat())
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Moonlight.copy(alpha = 0.9f * fade), Color.Transparent),
                        start = head, end = tail
                    ),
                    start = head, end = tail, strokeWidth = 1.6f * density, cap = StrokeCap.Round
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(260.dp)) {
                val stages = IntroStages(timeline.value)
                drawEmblem(
                    scale = size.width / 250f,
                    glowIn = stages.glow, glowPulse = glowPulse.value, orbitIn = stages.orbit, moonIn = stages.moon,
                    starIn = stages.star, wavesIn = stages.waves, waveDrift = waveDrift.value
                )
            }

            Spacer(Modifier.height(14.dp))

            val name = "MindTrack"
            Row {
                name.forEachIndexed { index, letter ->
                    Text(
                        text = letter.toString(),
                        color = SplashText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer {
                            val word = IntroStages(timeline.value).word
                            val local = ease(((word * (name.length + 4)) - index) / 4f)
                            alpha = local
                            translationY = (1f - local) * 14.dp.toPx()
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = tr("YOUR SPACE TO RESET"),
                color = SplashLavender,
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = ease(window(IntroStages(timeline.value).word, 0.5f, 0.5f)) * 0.9f
                }
            )
        }
    }
}

private fun DrawScope.drawEmblem(
    scale: Float,
    glowIn: Float,
    glowPulse: Float,
    orbitIn: Float,
    moonIn: Float,
    starIn: Float,
    wavesIn: Float,
    waveDrift: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Soft bloom behind everything.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                SplashLavender.copy(alpha = 0.30f * glowPulse * glowIn),
                SplashLavenderBright.copy(alpha = 0.10f * glowIn),
                Color.Transparent
            ),
            center = Offset(cx, cy), radius = 120f * scale
        ),
        radius = 120f * scale, center = Offset(cx, cy)
    )

    // Orbit draws itself, led by a bright particle.
    if (orbitIn > 0f) {
        val orbit = Path().apply {
            val steps = (120 * orbitIn).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val a = (-90.0 + 360.0 * i / 120.0) * PI / 180.0
                val x = cx + 77f * scale * cos(a).toFloat()
                val y = cy + 87f * scale * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(orbit, SplashLavender.copy(alpha = 0.65f), style = Stroke(1.2f * scale, cap = StrokeCap.Round))
        val a = (-90.0 + 360.0 * orbitIn) * PI / 180.0
        drawCircle(
            Moonlight.copy(alpha = 1f - 0.3f * orbitIn), 3.2f * scale,
            Offset(cx + 77f * scale * cos(a).toFloat(), cy + 87f * scale * sin(a).toFloat())
        )
    }

    // Moon rises into the orbit, easing out of a slight tilt.
    if (moonIn > 0f) {
        val rise = (1f - moonIn) * 16f * scale
        rotate(degrees = (1f - moonIn) * -14f, pivot = Offset(cx, cy)) {
            val moon = Path().apply {
                moveTo(cx - 8f * scale, cy - 59f * scale + rise)
                cubicTo(cx - 53f * scale, cy - 55f * scale + rise, cx - 66f * scale, cy - 5f * scale + rise, cx - 42f * scale, cy + 28f * scale + rise)
                cubicTo(cx - 16f * scale, cy + 63f * scale + rise, cx + 32f * scale, cy + 49f * scale + rise, cx + 47f * scale, cy + 12f * scale + rise)
                cubicTo(cx + 17f * scale, cy + 21f * scale + rise, cx - 12f * scale, cy - 3f * scale + rise, cx - 8f * scale, cy - 59f * scale + rise)
                close()
            }
            drawPath(
                moon,
                brush = Brush.linearGradient(
                    listOf(Moonlight, SplashLavender, SplashLavenderBright),
                    start = Offset(cx - 42f * scale, cy - 58f * scale),
                    end = Offset(cx + 43f * scale, cy + 53f * scale)
                ),
                alpha = moonIn
            )
            drawPath(moon, Color.White.copy(alpha = 0.7f * moonIn), style = Stroke(1.25f * scale))
        }
    }

    // Four-point star pops in with an expanding ring.
    if (starIn > 0f) {
        val sx = cx + 54f * scale
        val sy = cy - 48f * scale
        val pop = ease(starIn)
        val k = 0.3f + 0.7f * pop
        val star = Path().apply {
            moveTo(sx, sy - 13f * scale * k)
            quadraticTo(sx + 3f * scale * k, sy - 3f * scale * k, sx + 12f * scale * k, sy)
            quadraticTo(sx + 3f * scale * k, sy + 3f * scale * k, sx, sy + 13f * scale * k)
            quadraticTo(sx - 3f * scale * k, sy + 3f * scale * k, sx - 12f * scale * k, sy)
            quadraticTo(sx - 3f * scale * k, sy - 3f * scale * k, sx, sy - 13f * scale * k)
            close()
        }
        drawPath(star, Brush.linearGradient(listOf(Color.White, SplashLavender)), alpha = pop)
        if (starIn < 1f) {
            drawCircle(
                Moonlight.copy(alpha = 0.5f * (1f - starIn)), 6f * scale + 26f * scale * starIn,
                Offset(sx, sy), style = Stroke(1.2f * scale)
            )
        }
    }

    // Waves roll in from the left.
    if (wavesIn > 0f) {
        val reveal = size.width * (0.1f + 0.9f * wavesIn)
        val waveOne = Path().apply {
            moveTo(30f * scale, (198f + waveDrift) * scale)
            cubicTo(65f * scale, 177f * scale, 91f * scale, 211f * scale, 125f * scale, 194f * scale)
            cubicTo(160f * scale, 177f * scale, 187f * scale, 190f * scale, 220f * scale, 181f * scale)
        }
        val waveTwo = Path().apply {
            moveTo(35f * scale, (208f + waveDrift) * scale)
            cubicTo(72f * scale, 190f * scale, 97f * scale, 220f * scale, 132f * scale, 205f * scale)
            cubicTo(164f * scale, 191f * scale, 189f * scale, 202f * scale, 215f * scale, 196f * scale)
        }
        clipRect(left = 0f, top = 0f, right = reveal, bottom = size.height) {
            drawPath(waveOne, SplashLavender.copy(alpha = 0.85f), style = Stroke(2f * scale, cap = StrokeCap.Round))
            drawPath(waveTwo, SplashLavenderBright.copy(alpha = 0.7f), style = Stroke(1.5f * scale, cap = StrokeCap.Round))
        }
    }
}
