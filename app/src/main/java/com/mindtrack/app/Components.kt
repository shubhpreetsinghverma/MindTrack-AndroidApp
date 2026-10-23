package com.mindtrack.app

import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
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
// SHARED DESIGN COMPONENTS
// ------------------------------------------------------------

@Composable
internal fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Column {
        Text(
            eyebrow,
            color = Lavender,
            style = PixelLabelStyle
        )

        Spacer(Modifier.height(8.dp))

        Text(
            title,
            color = TextPrimary,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
internal fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(5.dp))

        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun MetricCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .quietCard(16.dp)
            .padding(15.dp)
    ) {
        Text(
            title,
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            value,
            color = Lavender,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(5.dp))

        Text(
            caption,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun EmptyState(
    emoji: String,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            emoji,
            color = Lavender,
            fontSize = 29.sp
        )

        Spacer(Modifier.height(9.dp))

        Text(
            title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(5.dp))

        Text(
            message,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun InsightCard(
    records: List<CheckInRecord>,
    suggestions: SuggestionsViewModel
) {
    var suggestionsExpanded by remember { mutableStateOf(false) }

    val summary = personalizedInsight(records)
    val suggestionsText = suggestions.text
    val isLoadingSuggestions = suggestions.isLoading
    var noRecordsNotice by remember { mutableStateOf(false) }
    val suggestionsError = suggestions.error
        ?: if (noRecordsNotice) tr("Record a check-in first so your suggestions can be relevant to you.") else null
    val parsedSuggestions = suggestionsText
        .lines()
        .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("1.").removePrefix("2.").removePrefix("3.").removePrefix("4.").removePrefix("5.").trim() }
        .filter { it.isNotBlank() }

    fun requestSuggestions() = suggestions.load(records, summary)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            (if (records.isEmpty()) tr("Your summary will appear here") else tr("What your check-ins are showing")).uppercase(),
            color = Lavender,
            style = PixelLabelStyle
        )

        Spacer(Modifier.height(8.dp))

        Text(
            summary,
            color = TextPrimary,
            fontSize = 15.sp,
            lineHeight = 23.sp
        )

        Spacer(Modifier.height(14.dp))

        TextButton(
            onClick = {
                suggestionsExpanded = !suggestionsExpanded
                if (suggestionsExpanded && suggestionsText.isBlank() && !isLoadingSuggestions) {
                    if (records.isEmpty()) {
                        noRecordsNotice = true
                    } else {
                        requestSuggestions()
                    }
                }
            },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(
                if (suggestionsExpanded) tr("Suggestions  ▴") else tr("Suggestions  ▾"),
                color = Lavender,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (suggestionsExpanded) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .quietCard(12.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    tr("Small next steps, based on this summary").uppercase(),
                    color = TextSecondary,
                    style = PixelLabelStyle.copy(fontSize = 10.sp)
                )

                if (isLoadingSuggestions && parsedSuggestions.isEmpty()) {
                    Text(tr("Thinking through your check-ins…"), color = TextSecondary, fontSize = 12.sp)
                }

                parsedSuggestions.forEach { suggestion ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = Lavender, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            suggestion,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                suggestionsError?.let { error ->
                    Text(error, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    if (records.isNotEmpty()) {
                        TextButton(
                            onClick = { requestSuggestions() }
                        ) {
                            Text(tr("Try again"), color = Lavender)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RatingSlider(
    title: String,
    subtitle: String,
    value: Int,
    lowLabel: String,
    highLabel: String,
    onValueChange: (Int) -> Unit
) {
    HomeSection {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "$value / 5",
                color = Lavender,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        Spacer(Modifier.height(12.dp))
        BlockMeter(title, value, onValueChange)

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(lowLabel, color = TextSecondary, fontSize = 11.sp)
            Text(highLabel, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun InfoCard(
    icon: String,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceColor)
            .padding(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            icon,
            color = Lavender,
            fontSize = 26.sp
        )

        Spacer(Modifier.width(13.dp))

        Column {
            Text(
                title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(7.dp))

            Text(
                body,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ------------------------------------------------------------
// BOTTOM NAVIGATION
// ------------------------------------------------------------

/** Pixel icons for the five tabs, 9 columns wide. A "#" is a filled square. */
private val tabPixels: Map<AppTab, List<String>> = mapOf(
    AppTab.HOME to listOf(
        "....#....", "...###...", "..#####..", ".#######.",
        "..#...#..", "..#...#..", "..#.#.#..", "..#.#.#..", "..#####.."
    ),
    AppTab.INSIGHTS to listOf(
        ".........", ".........", "....##...", "....##...",
        "....##.##", "##..##.##", "##..##.##", "##..##.##", "#########"
    ),
    AppTab.COMPANION to listOf(
        ".........", "..##.##..", ".#######.", "#########",
        "#########", ".#######.", "..#####..", "...###...", "....#...."
    ),
    AppTab.PROFILE to listOf(
        "...###...", "..#####..", "..#####..", "...###...",
        ".........", ".#######.", "#########", "#########", "#########"
    )
)

@Composable
private fun TabIcon(tab: AppTab, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val unit = kotlin.math.floor(size.width / 9f).coerceAtLeast(1f)
        val origin = (size.width - unit * 9f) / 2f
        if (tab == AppTab.MOOD) {
            // The mood tab uses the same little crescent as the rest of the app.
            val cell = size.width / 12f
            moonCells.forEach { (x, y) -> drawRect(tint, Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f)) }
        } else {
            tabPixels.getValue(tab).forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == '#') drawRect(tint, Offset(origin + x * unit, origin + y * unit), androidx.compose.ui.geometry.Size(unit, unit))
                }
            }
        }
    }
}

@Composable
internal fun BottomNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Surface(
        color = ThemeState.palette.navSurface,
        modifier = Modifier.drawBehind {
            // A hairline instead of a shadow keeps the bar flat and quiet.
            drawLine(Border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val press = remember { MutableInteractionSource() }
                val tint by animateColorAsState(
                    if (selected) Lavender else TextSecondary.copy(alpha = 0.75f),
                    tween(180), label = "navTint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(press, 0.94f)
                        .selectable(
                            selected = selected,
                            interactionSource = press,
                            indication = null,
                            role = Role.Tab
                        ) { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // A short bar above the active tab, like a cursor on a handheld screen.
                    Box(
                        Modifier.padding(bottom = 8.dp).size(width = 22.dp, height = 3.dp)
                            .background(if (selected) Lavender else Color.Transparent)
                    )
                    Box(Modifier.clearAndSetSemantics {}) { TabIcon(tab, tint) }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        tr(tab.label),
                        color = tint,
                        style = PixelLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
            }
        }
    }
}

/** Gentle scale-down while pressed. Place it first in the chain so the whole card scales. */
internal fun Modifier.pressScale(source: androidx.compose.foundation.interaction.InteractionSource, pressedScale: Float = 0.97f): Modifier =
    composed {
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) pressedScale else 1f, tween(110), label = "pressScale")
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TagChips(selected: List<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        moodTags.forEach { tag ->
            val on = tag in selected
            Box(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Lavender.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) Lavender else Border, RoundedCornerShape(10.dp))
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(tag) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr(tag), color = if (on) Lavender else TextSecondary, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}


/** A quiet card: just a hairline outline on the page colour, no fill. */
internal fun Modifier.quietCard(radius: androidx.compose.ui.unit.Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).border(1.dp, Border, shape)
}


/** A flat section: a hairline above it and some air, with no box. Same look as the sections on Home. */
internal fun Modifier.flatSection(): Modifier = this
    .drawBehind { drawLine(Border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
    .padding(top = 18.dp)

/** Five blocks instead of a slider: tap the number that fits, and everything up to it fills in. */
@Composable
internal fun BlockMeter(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..5).forEach { step ->
            val on = step <= value
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(shape)
                    .background(if (on) Lavender else Color.Transparent)
                    .border(1.dp, if (on) Lavender else Border, shape)
                    .selectable(selected = step == value, role = Role.RadioButton) { onValueChange(step) }
                    .semantics {
                        contentDescription = "$title $step"
                        stateDescription = trf("%1\$d out of 5", step)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$step",
                    color = if (on) Background else TextSecondary,
                    style = PixelLabelStyle.copy(letterSpacing = 0.sp),
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}


/** Text-field colours for the flat theme: a thin outline that turns lavender when you type. */
@Composable
internal fun quietFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lavender,
    unfocusedBorderColor = Border,
    disabledBorderColor = Border,
    cursorColor = Lavender,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Lavender,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary
)
