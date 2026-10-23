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
// HOME
// Kept deliberately small: a greeting, today's check-in, and a kind line. Everything else lives behind
// the Guide (what MindTrack is) and Resources (tools and support) buttons.
// ------------------------------------------------------------

@Composable
internal fun HomeScreen(
    today: LocalDate,
    mood: Int,
    checkedIn: Boolean,
    records: List<CheckInRecord>,
    streak: Int = 0,
    allRecords: List<CheckInRecord> = records,
    dismissedInsights: Set<String> = emptySet(),
    onInsightsClick: () -> Unit = {},
    name: String? = null,
    onHistoryClick: () -> Unit = {},
    onExercisesClick: () -> Unit = {},
    onCheckInClick: () -> Unit,
    onResourcesClick: () -> Unit,
    onGuideClick: () -> Unit
) {
    // A Low or Rough day in the last day or so gets a gentler tone instead of jokes.
    val latest = records.maxByOrNull { it.date }
    val softTone = latest != null && latest.mood <= 2 && latest.date >= today.minusDays(1).toString()
    val hour = java.time.LocalTime.now().hour
    val language = Lang.current
    val greeting = remember(name, hour / 4, softTone, language) {
        greetingFor(firstNameOf(name), hour, softTone, kotlin.random.Random(HomeSeed.greetingSeed))
    }
    val reminder = remember(language) { reminderFor(kotlin.random.Random(HomeSeed.reminderSeed)) }
    val moonMood = when {
        softTone -> MoonMood.GENTLE
        checkedIn -> moonMoodFor(mood)
        else -> MoonMood.SLEEPY
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEE d MMM", appLocale())).uppercase(Locale.getDefault()),
                    color = TextSecondary, style = PixelLabelStyle
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeIconButton(label = tr("GUIDE"), description = tr("Meet MindTrack"), onClick = onGuideClick) { GuideIcon() }
                    HomeIconButton(label = tr("RESOURCES"), description = tr("Resources"), onClick = onResourcesClick) { ResourcesIcon() }
                }
            }
        }

        item {
            Column {
                PixelMoon(size = 52.dp, mood = moonMood, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.height(14.dp))
                Text(greeting.title, color = TextPrimary, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(greeting.line, color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, TextPrimary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onCheckInClick)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (checkedIn) tr("TODAY'S MOOD") else tr("DAILY CHECK-IN"),
                        color = TextSecondary, style = PixelLabelStyle
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (checkedIn) moodOptions.firstOrNull { it.score == mood }?.label?.let { tr(it) } ?: tr("Okay")
                        else tr("A few minutes for yourself"),
                        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
                    )
                }
                Text(if (checkedIn) tr("Update  →") else tr("Begin  →"), color = Lavender, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item { WeekGlanceCard(remember(allRecords, today) { weekOf(allRecords, today) }, streak) }

        // Only appears once there are enough check-ins for a pattern, and goes away if it is dismissed in Insights.
        topPattern(allRecords, dismissedInsights)?.let { item { PatternLineCard(it, onInsightsClick) } }

        onThisDayFor(allRecords, today)?.let { item { OnThisDayCard(it) } }

        item { GentleReminderLine(reminder) }

        item {
            Column {
                SmallThingCard(remember(allRecords, today, language) { smallThingFor(allRecords, today) })
                Spacer(Modifier.height(20.dp))
                BreatheCard(onExercisesClick)
                Spacer(Modifier.height(20.dp))
                HistoryCard(allRecords, onHistoryClick)
            }
        }
    }
}

@Composable
private fun HomeIconButton(label: String, description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceColor)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { icon() }
        Text(label, color = TextSecondary, fontSize = 8.sp, letterSpacing = 1.2.sp, fontFamily = PixelFont)
    }
}

/** A crisp open-book mark, so it reads clearly as a guide. */
@Composable
private fun GuideIcon() {
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

/** Four rounded tiles: the tools and support behind this button. */
@Composable
private fun ResourcesIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val ink = ThemeState.palette.guideInk
        val gap = size.width * 0.12f
        val tile = (size.width - gap) / 2f
        val radius = androidx.compose.ui.geometry.CornerRadius(tile * 0.28f)
        for (row in 0..1) for (col in 0..1) {
            drawRoundRect(
                color = if (row == 0 && col == 1) ink.copy(alpha = 0.55f) else ink,
                topLeft = Offset(col * (tile + gap), row * (tile + gap)),
                size = androidx.compose.ui.geometry.Size(tile, tile),
                cornerRadius = radius
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
