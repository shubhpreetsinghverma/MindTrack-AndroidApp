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
            fontSize = 10.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold
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
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceColor)
            .padding(17.dp)
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
            fontWeight = FontWeight.Bold
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceElevated)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Lavender.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✧", color = Lavender, fontSize = 24.sp)
            }

            Spacer(Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (records.isEmpty()) tr("Your summary will appear here") else tr("What your check-ins are showing"),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(7.dp))

                Text(
                    summary,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background.copy(alpha = 0.55f))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    tr("Small next steps, based on this summary"),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceColor)
            .padding(18.dp)
    ) {
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

        Slider(
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = trf("%1\$d out of 5", value)
            },
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.roundToInt().coerceIn(1, 5))
            },
            valueRange = 1f..5f,
            steps = 3
        )

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

@Composable
internal fun BottomNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Surface(
        color = ThemeState.palette.navSurface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val press = remember { MutableInteractionSource() }
                val pill by animateColorAsState(
                    if (selected) Lavender.copy(alpha = 0.14f) else Color.Transparent,
                    tween(220), label = "navPill"
                )
                val tint by animateColorAsState(
                    if (selected) Lavender else TextSecondary,
                    tween(220), label = "navTint"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(press, 0.94f)
                        .clip(RoundedCornerShape(15.dp))
                        .selectable(
                            selected = selected,
                            interactionSource = press,
                            indication = null,
                            role = Role.Tab
                        ) { onTabSelected(tab) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pill)
                            .padding(
                                horizontal = 14.dp,
                                vertical = 5.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab.icon,
                            color = tint,
                            fontSize = 22.sp,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        tr(tab.label),
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (selected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        textAlign = TextAlign.Center
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
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Lavender.copy(alpha = 0.18f) else SurfaceElevated)
                    .border(1.dp, if (on) Lavender else Border, RoundedCornerShape(20.dp))
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(tag) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr(tag), color = if (on) Lavender else TextSecondary, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
