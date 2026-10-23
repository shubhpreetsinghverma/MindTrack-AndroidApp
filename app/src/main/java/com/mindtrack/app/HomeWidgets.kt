package com.mindtrack.app

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** A section on Home: a hairline above it and plenty of air, with no box around it. */
@Composable
internal fun HomeSection(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(Border, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(top = 18.dp),
        content = content
    )
}

/** Monday to Sunday as seven dots. Tap a day to see how it went. */
@Composable
internal fun WeekGlanceCard(week: List<WeekDay>, streak: Int = 0) {
    val (done, passed) = weekProgress(week)
    var selected by remember { mutableStateOf<WeekDay?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMM", appLocale()) }

    HomeSection {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("This week").uppercase(), color = TextSecondary, style = PixelLabelStyle)
            Text(trf("%1\$d of %2\$d days", done, passed), color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { day ->
                val mood = day.record?.mood
                val label = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale())
                val description = day.date.format(dateFormat) + ": " +
                    (mood?.let { tr(moodOptions.first { o -> o.score == it }.label) } ?: tr("No check-in"))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = description) { selected = if (selected == day) null else day }
                        .semantics { contentDescription = description }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(label, color = if (day.isToday) TextPrimary else TextSecondary, fontSize = 11.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clearAndSetSemantics {})
                    Spacer(Modifier.height(8.dp))
                    val ring = when {
                        day.isToday -> BorderStroke(1.5.dp, TextPrimary)
                        mood == null && !day.isFuture -> BorderStroke(1.dp, Border)
                        else -> null
                    }
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (mood != null) moodColorFor(mood) else Color.Transparent)
                            .let { if (ring != null) it.border(ring, CircleShape) else it }
                    )
                }
            }
        }
        if (streak > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (streak == 1) tr("1 day of checking in") else trf("%1\$d days in a row", streak),
                color = TextSecondary, fontSize = 12.sp
            )
        }
        selected?.let { day ->
            Spacer(Modifier.height(10.dp))
            val r = day.record
            Text(
                day.date.format(dateFormat) + "  ·  " + if (r == null) tr("No check-in") else
                    trf("%1\$s, stress %2\$d/5, energy %3\$d/5", tr(moodOptions.first { it.score == r.mood }.label), r.stress, r.energy),
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

/** A single, small, kind thing to do today. */
@Composable
internal fun SmallThingCard(thing: SmallThing) {
    HomeSection {
        Text(tr("ONE SMALL THING FOR TODAY"), color = Lavender, style = PixelLabelStyle)
        Spacer(Modifier.height(8.dp))
        Text(thing.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

/** How a week ago, or a month ago, felt. Shown only when there is a check-in from then. */
@Composable
internal fun OnThisDayCard(item: OnThisDay) {
    val option = moodOptions.first { it.score == item.record.mood }
    HomeSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            MoodFace(score = option.score, selected = false, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(item.label).uppercase(), color = TextSecondary, style = PixelLabelStyle)
                Spacer(Modifier.height(4.dp))
                Text(trf("You felt %1\$s.", tr(option.label).lowercase()), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** One short kind line, set apart in italics. */
@Composable
internal fun GentleReminderLine(text: String) {
    HomeSection {
        Text(tr("A gentle reminder").uppercase(), color = TextSecondary, style = PixelLabelStyle)
        Spacer(Modifier.height(6.dp))
        Text(text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

/** A plain row with a chevron, for the shortcuts under the widgets. */
@Composable
internal fun SupportRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(Border, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** One pattern from the person's own check-ins, in a single line. Tapping it opens Insights for the full picture. */
@Composable
internal fun PatternLineCard(pattern: PatternInsight, onClick: () -> Unit) {
    HomeSection {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
        ) {
            Text(tr("A PATTERN WE NOTICED"), color = Lavender, style = PixelLabelStyle)
            Spacer(Modifier.height(8.dp))
            Text(pattern.title, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(pattern.detail, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            Text(tr("See all patterns  →"), color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A little pixel notebook, drawn from squares like the rest of the app. */
@Composable
private fun PixelNotebook(tint: Color, modifier: Modifier = Modifier) {
    val rows = listOf(
        ".#######.", ".#.....#.", ".#.###.#.", ".#.....#.", ".#.###.#.",
        ".#.....#.", ".#.##..#.", ".#.....#.", ".#######."
    )
    androidx.compose.foundation.Canvas(modifier.size(30.dp)) {
        val unit = kotlin.math.floor(size.width / 9f).coerceAtLeast(1f)
        val origin = (size.width - unit * 9f) / 2f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') drawRect(tint, androidx.compose.ui.geometry.Offset(origin + x * unit, origin + y * unit), androidx.compose.ui.geometry.Size(unit, unit))
            }
        }
    }
}

/**
 * The History shortcut: a pixel notebook and how many check-ins it holds. It never shows what you wrote,
 * so a glance at the screen reveals nothing private and no hard day is brought back by surprise.
 */
@Composable
internal fun HistoryCard(records: List<CheckInRecord>, onClick: () -> Unit) {
    val first = remember(records) { records.minOfOrNull { it.date } }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM", appLocale()) }
    HomeSection {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
        ) {
            Text(tr("YOUR HISTORY"), color = Lavender, style = PixelLabelStyle)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelNotebook(Lavender, Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (records.size) {
                            0 -> tr("Your notebook is waiting")
                            1 -> tr("1 check-in saved")
                            else -> trf("%1\$d check-ins saved", records.size)
                        },
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium
                    )
                    if (first != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(trf("Since %1\$s", java.time.LocalDate.parse(first).format(dateFormat)), color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(tr("Open history  →"), color = Lavender, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A tiny pixel breath: a square that grows and shrinks, like a chest rising and falling. */
@Composable
private fun PixelBreath(tint: Color, modifier: Modifier = Modifier) {
    val still = remember { !android.animation.ValueAnimator.areAnimatorsEnabled() }
    val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val phase = clock.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "phase"
    )
    androidx.compose.foundation.Canvas(modifier.size(30.dp)) {
        // Three sizes only, so it steps like a sprite instead of gliding.
        val level = if (still) 1 else kotlin.math.floor(phase.value * 2.99f).toInt()
        val cells = 5 + level * 2                // 5, 7 or 9 squares wide
        val unit = kotlin.math.floor(size.width / 9f).coerceAtLeast(1f)
        val side = unit * cells
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val ring = unit
        drawRect(tint, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(side, ring))
        drawRect(tint, androidx.compose.ui.geometry.Offset(left, top + side - ring), androidx.compose.ui.geometry.Size(side, ring))
        drawRect(tint, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(ring, side))
        drawRect(tint, androidx.compose.ui.geometry.Offset(left + side - ring, top), androidx.compose.ui.geometry.Size(ring, side))
    }
}

/** The breathing and grounding shortcut, set out like the other sections, with a small breathing square. */
@Composable
internal fun BreatheCard(onClick: () -> Unit) {
    HomeSection {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
        ) {
            Text(tr("BREATHE AND GROUND"), color = Lavender, style = PixelLabelStyle)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelBreath(Lavender, Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Short calming exercises"), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(tr("Breathing, grounding and body relaxation"), color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(tr("Start an exercise  →"), color = Lavender, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
