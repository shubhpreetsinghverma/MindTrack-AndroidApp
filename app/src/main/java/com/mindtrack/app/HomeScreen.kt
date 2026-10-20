package com.mindtrack.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------
// HOME DASHBOARD
// ------------------------------------------------------------

@Composable
internal fun HomeScreen(
    today: LocalDate,
    mood: Int,
    checkedIn: Boolean,
    records: List<CheckInRecord>,
    streak: Int = 0,
    onHistoryClick: () -> Unit = {},
    onExercisesClick: () -> Unit = {},
    onCheckInClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onResourceClick: () -> Unit,
    onAboutClick: () -> Unit,
    onMoodOptionClick: (MoodOption) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        tr("MindTrack"),
                        modifier = Modifier.clickable(onClick = onAboutClick),
                        color = TextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        tr("YOUR SPACE TO RESET"),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = ThemeState.palette.guideGradient
                                )
                            )
                            .semantics { contentDescription = tr("Resource guide") }
                            .clickable(role = Role.Button, onClick = onResourceClick),
                        contentAlignment = Alignment.Center
                    ) {
                        // A crisp open-book mark makes this clearly a guide, not a generic sparkle.
                        Canvas(modifier = Modifier.size(27.dp)) {
                            val ink = ThemeState.palette.guideInk
                            val strokeWidth = 1.8.dp.toPx()
                            val leftPage = Path().apply {
                                moveTo(size.width * 0.47f, size.height * 0.27f)
                                cubicTo(size.width * 0.34f, size.height * 0.17f, size.width * 0.18f, size.height * 0.18f, size.width * 0.08f, size.height * 0.23f)
                                lineTo(size.width * 0.08f, size.height * 0.76f)
                                cubicTo(size.width * 0.23f, size.height * 0.69f, size.width * 0.37f, size.height * 0.73f, size.width * 0.47f, size.height * 0.84f)
                                close()
                            }
                            val rightPage = Path().apply {
                                moveTo(size.width * 0.53f, size.height * 0.27f)
                                cubicTo(size.width * 0.66f, size.height * 0.17f, size.width * 0.82f, size.height * 0.18f, size.width * 0.92f, size.height * 0.23f)
                                lineTo(size.width * 0.92f, size.height * 0.76f)
                                cubicTo(size.width * 0.77f, size.height * 0.69f, size.width * 0.63f, size.height * 0.73f, size.width * 0.53f, size.height * 0.84f)
                                close()
                            }
                            drawPath(leftPage, ink, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                            drawPath(rightPage, ink, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.28f), Offset(size.width * 0.5f, size.height * 0.83f), strokeWidth, cap = StrokeCap.Round)
                            drawLine(ink.copy(alpha = 0.7f), Offset(size.width * 0.17f, size.height * 0.38f), Offset(size.width * 0.34f, size.height * 0.39f), strokeWidth * 0.65f, cap = StrokeCap.Round)
                            drawLine(ink.copy(alpha = 0.7f), Offset(size.width * 0.66f, size.height * 0.39f), Offset(size.width * 0.83f, size.height * 0.38f), strokeWidth * 0.65f, cap = StrokeCap.Round)
                        }
                    }
                    Text(
                        text = tr("GUIDE"),
                        color = Lavender,
                        fontSize = 8.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceColor)
                    .drawBehind {
                        // A faint lavender glow in the corner: depth without decoration.
                        val glowCentre = Offset(size.width * 0.92f, 0f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Lavender.copy(alpha = 0.15f), Color.Transparent),
                                center = glowCentre, radius = size.width * 0.7f
                            ),
                            radius = size.width * 0.7f, center = glowCentre
                        )
                    }
                    .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                Text(
                    today.format(
                        DateTimeFormatter.ofPattern(
                            "EEEE, d MMMM",
                            appLocale()
                        )
                    ).uppercase(Locale.getDefault()),
                    color = Lavender,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    if (checkedIn) {
                        tr("You showed up for yourself today.")
                    } else {
                        tr("A little space\njust for you.")
                    },
                    color = TextPrimary,
                    fontSize = 29.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(9.dp))

                Text(
                    if (checkedIn) {
                        tr("Take a moment to notice how you're feeling now.")
                    } else {
                        tr("Take a breath. How are you, really?")
                    },
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (checkedIn) tr("TODAY'S MOOD") else tr("DAILY CHECK-IN"),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            if (checkedIn) {
                                moodOptions.firstOrNull {
                                    it.score == mood
                                }?.label?.let { tr(it) }
                                    ?: tr("Okay")
                            } else {
                                tr("A few minutes for yourself")
                            },
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onCheckInClick,
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lavender,
                            contentColor = Background
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                    ) {
                        Text(
                            if (checkedIn) tr("Update  →") else tr("Begin  →"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            SectionHeading(
                title = tr("How are you feeling?"),
                subtitle = tr("Whatever you're feeling, it belongs here.")
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moodOptions.forEach { option ->
                    // Home cards are neutral by default. Only a mouse/trackpad hover
                    // adds a temporary lavender highlight; saved mood does not select a card.
                    val hoverInteraction = remember(option.score) { MutableInteractionSource() }
                    val isHovered by hoverInteraction.collectIsHoveredAsState()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pressScale(hoverInteraction)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (isHovered) SurfaceSelected else SurfaceColor)
                            .hoverable(interactionSource = hoverInteraction)
                            .clickable(
                                interactionSource = hoverInteraction,
                                indication = null,
                                onClickLabel = trf("Feeling %1\$s", tr(option.label).lowercase()),
                                role = Role.Button
                            ) { onMoodOptionClick(option) }
                            .padding(vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MoodFace(score = option.score, selected = isHovered, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(7.dp))
                        Text(
                            tr(option.label),
                            color = if (isHovered) Lavender else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isHovered) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(9.dp))

            Text(
                tr("Your feelings aren't a problem to solve."),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceElevated)
                    .padding(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("☁", fontSize = 27.sp, color = Lavender)
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        tr("A gentle reminder"),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("You don't have to figure everything out today."),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInsightsClick),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceColor
                )
            ) {
                Column(Modifier.padding(19.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                tr("Your mood journey"),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("Small moments, meaningful patterns"),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            "↗",
                            color = Lavender,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    if (records.isEmpty()) {
                        EmptyState(
                            emoji = "✧",
                            title = tr("Your story starts here"),
                            message = tr("Save a check-in to start seeing your personal mood trend.")
                        )
                    } else {
                        MoodTrendChart(records = records)
                    }
                }
            }
        }

        if (streak > 0) {
            item { StreakChip(streak) }
        }

        item {
            HomeQuickTools(
                onCheckInClick = onCheckInClick,
                onInsightsClick = onInsightsClick,
                onResourceClick = onResourceClick,
                onHistoryClick = onHistoryClick,
                onExercisesClick = onExercisesClick
            )
        }
    }
}

@Composable
internal fun StreakChip(streak: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✦", color = Lavender, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (streak == 1) tr("1 day of checking in") else trf("%1\$d days in a row", streak),
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                tr("Every check-in counts. There's no pressure to be perfect."),
                color = TextSecondary, fontSize = 11.sp
            )
        }
    }
}
