package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
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
// INSIGHTS
// ------------------------------------------------------------

@Composable
internal fun InsightsScreen(
    records: List<CheckInRecord>,
    checkedInToday: Boolean,
    weekly: WeeklySummary? = null,
    suggestions: SuggestionsViewModel,
    allRecords: List<CheckInRecord> = emptyList(),
    dismissedInsights: Set<String> = emptySet(),
    onDismissInsight: (String) -> Unit = {}
) {
    val recentRecords = records.filter { record ->
        runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { date ->
            !date.isBefore(LocalDate.now().minusDays(6)) && !date.isAfter(LocalDate.now())
        } ?: false
    }.sortedBy { it.date }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 18.dp,
            bottom = 30.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // The little moon reflects the week: smiling on a good one, gentle on a hard one.
            PixelMoon(
                size = 44.dp,
                mood = weekly?.averageMood?.let { if (it >= 4.0) MoonMood.HAPPY else if (it <= 2.0) MoonMood.GENTLE else MoonMood.SLEEPY } ?: MoonMood.SLEEPY,
                modifier = Modifier.padding(bottom = 12.dp).clearAndSetSemantics {}
            )
            ScreenHeader(
                eyebrow = tr("YOUR PERSONAL JOURNEY"),
                title = tr("Your recent check-ins, in perspective"),
                subtitle = tr("A summary of what you've logged — whether that's one day or a full week.")
            )
        }

        item {
            InsightCard(records = recentRecords, suggestions = suggestions)
        }

        if (weekly != null) {
            item { WeeklySummaryCard(weekly) }
        }

        item {
            PatternsSection(allRecords = allRecords, dismissed = dismissedInsights, onDismiss = onDismissInsight)
        }

        item { ForecastCard(allRecords) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                MetricCard(
                    title = tr("Check-ins"),
                    value = recentRecords.size.toString(),
                    caption = tr("Check-ins in this period"),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = tr("Average mood"),
                    value = if (recentRecords.isEmpty()) {
                        "—"
                    } else {
                        String.format(
                            Locale.getDefault(),
                            "%.1f/5",
                            recentRecords.map { it.mood }.average()
                        )
                    },
                    caption = tr("Average mood in this period"),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .flatSection()
            ) {
                Text(
                    tr("Your mood over time"),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    tr("Based on the check-ins included in this dashboard"),
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                if (recentRecords.isEmpty()) {
                    EmptyState(
                        emoji = "◌",
                        title = tr("No patterns just yet"),
                        message = tr("Check in regularly to build your personal mood timeline.")
                    )
                } else {
                    MoodTrendChart(
                        records = recentRecords,
                        showScale = true
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .flatSection()
            ) {
                Text(
                    tr("Your wellbeing snapshot"),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(15.dp))

                if (recentRecords.isEmpty()) {
                    Text(
                        tr("Your personal summary will appear here after your first check-in."),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    AverageRow(
                        label = tr("Mood"),
                        value = recentRecords.map { it.mood }.average(),
                        color = Lavender
                    )

                    Spacer(Modifier.height(15.dp))

                    AverageRow(
                        label = tr("Stress"),
                        value = recentRecords.map { it.stress }.average(),
                        color = Coral
                    )

                    Spacer(Modifier.height(15.dp))

                    AverageRow(
                        label = tr("Energy"),
                        value = recentRecords.map { it.energy }.average(),
                        color = Mint
                    )
                }
            }
        }

        item {
            Text(
                if (checkedInToday) {
                    tr("✓ Today's check-in is saved.")
                } else {
                    tr("Your next check-in can help you notice changes over time.")
                },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun AverageRow(
    label: String,
    value: Double,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(
                String.format(Locale.getDefault(), "%.1f / 5", value),
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / 5.0).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ------------------------------------------------------------
// MOOD CHART
// ------------------------------------------------------------

@Composable
internal fun MoodTrendChart(
    records: List<CheckInRecord>,
    showScale: Boolean = false
) {
    val points = records.sortedBy { it.date }

    if (points.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showScale) 175.dp else 125.dp)
    ) {
        val horizontalPadding = 10.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 12.dp.toPx()

        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = size.height - topPadding - bottomPadding

        for (i in 0..4) {
            val y = topPadding + chartHeight * i / 4f

            drawLine(
                color = Border,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val coordinates = points.mapIndexed { index, record ->
            val x = if (points.size == 1) {
                size.width / 2f
            } else {
                horizontalPadding +
                        chartWidth * index / (points.size - 1)
            }

            val y = topPadding +
                    chartHeight * (5 - record.mood) / 4f

            Offset(x, y)
        }

        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = LavenderBright,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        coordinates.forEach { point ->
            drawCircle(
                color = LavenderBright,
                radius = 5.dp.toPx(),
                center = point
            )
            drawCircle(
                color = SurfaceColor,
                radius = 2.dp.toPx(),
                center = point
            )
        }
    }

    Spacer(Modifier.height(5.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        points.forEach { record ->
            Text(
                LocalDate.parse(record.date).format(
                    DateTimeFormatter.ofPattern(
                        "EE",
                        Locale.getDefault()
                    )
                ),
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }

    if (showScale) {
        Spacer(Modifier.height(10.dp))
        Text(
            tr("1 = Rough     3 = Okay     5 = Great"),
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
internal fun WeeklySummaryCard(weekly: WeeklySummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .flatSection()
    ) {
        Text(tr("Your week"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(tr("The last 7 days at a glance."), color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatCard("${weekly.checkIns}/7", tr("Days"), Modifier.weight(1f))
            ProfileStatCard(formatScore(weekly.averageMood), tr("Avg mood"), Modifier.weight(1f))
            ProfileStatCard(formatScore(weekly.averageStress), tr("Avg stress"), Modifier.weight(1f))
            ProfileStatCard(formatScore(weekly.averageEnergy), tr("Avg energy"), Modifier.weight(1f))
        }
        val change = weekly.moodChange
        if (change != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    change >= 0.4 -> tr("Your mood averaged higher than the week before. Worth noticing what helped.")
                    change <= -0.4 -> tr("Your mood averaged lower than the week before. Be a little extra kind to yourself.")
                    else -> tr("Your mood was steady compared with the week before.")
                },
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
            )
        } else if (weekly.checkIns == 0) {
            Spacer(Modifier.height(12.dp))
            Text(tr("Check in to start your weekly summary."), color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PatternsSection(allRecords: List<CheckInRecord>, dismissed: Set<String>, onDismiss: (String) -> Unit) {
    val patterns = remember(allRecords) { findPatterns(allRecords) }.filter { it.id !in dismissed }

    Column(Modifier.fillMaxWidth().flatSection()) {
        Text(tr("Patterns worth noticing"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            tr("Drawn only from your own check-ins. These are possibilities to reflect on, not explanations or diagnoses."),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))

        when {
            allRecords.size < 14 -> Text(
                trf("Patterns need at least 14 check-ins. You have %1\$d. Adding tags to your check-ins makes them more useful.", allRecords.size),
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
            )
            patterns.isEmpty() -> Text(
                tr("Nothing stands out yet. That's fine: it often means your days have been fairly similar, or there isn't enough data to say."),
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                patterns.forEach { insight -> PatternRow(insight, onDismiss = { onDismiss(insight.id) }) }
            }
        }
    }
}

@Composable
private fun PatternRow(insight: PatternInsight, onDismiss: () -> Unit) {
    var showHow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Background.copy(alpha = 0.55f)).padding(14.dp)) {
        Text(insight.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(insight.detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        if (showHow) {
            Spacer(Modifier.height(8.dp))
            Text(tr("How this was worked out"), color = Lavender, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(insight.how, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Row {
            TextButton(onClick = { showHow = !showHow }) {
                Text(if (showHow) tr("Hide how") else tr("How was this worked out?"), color = Lavender, fontSize = 12.sp)
            }
            TextButton(onClick = onDismiss) { Text(tr("Dismiss"), color = TextSecondary, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ForecastCard(allRecords: List<CheckInRecord>) {
    val outcome = remember(allRecords) { evaluateForecast(allRecords.sortedBy { it.date }.map { it.mood }) }

    Column(Modifier.fillMaxWidth().flatSection()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Mood outlook"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(tr("EXPERIMENTAL"), color = Lavender, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr("A cautious estimate of your next mood rating. It is shown only if it has proven more accurate than simply using your average, tested on your own history. It is not a clinical assessment."),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        when (val o = outcome) {
            is ForecastOutcome.TooEarly -> Text(
                trf("Needs at least %1\$d check-ins to test fairly. You have %2\$d.", o.need, o.have),
                color = TextSecondary, fontSize = 13.sp
            )
            is ForecastOutcome.NotReliable -> Text(
                trf("No reliable outlook yet. Your mood hasn't been predictable enough from past entries to beat a plain average (typical miss of %1\$s points). That's a normal result, since moods are shaped by many things.", formatScore(o.baselineError)),
                color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
            )
            is ForecastOutcome.Ready -> {
                Text(trf("About %1\$s / 5", formatScore(o.predicted)), color = Lavender, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    trf(
                        "Based on %1\$s. When tested on %2\$d of your past entries it was off by about %3\$s points on average, versus %4\$s for your overall average. Treat it as a hint, not a prediction.",
                        tr(o.method), o.testedOn, formatScore(o.typicalError), formatScore(o.baselineError)
                    ),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
}
