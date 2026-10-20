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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
// ABOUT MINDTRACK
// ------------------------------------------------------------

@Composable
internal fun AboutMindTrackScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text(tr("←  Back to Home"), color = Lavender)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SurfaceColor).padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(tr("ABOUT THE APP"), color = Lavender, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(tr("Meet MindTrack"), color = TextPrimary, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("MindTrack is a personal wellbeing companion designed to help you pause, check in with yourself, and notice patterns in how you feel over time."),
                    color = TextSecondary, fontSize = 15.sp, lineHeight = 23.sp
                )
            }
        }
        item {
            AboutJourneyDiagram()
        }
        item {
            AboutMoodScaleFigure()
        }
        item {
            AboutSection(
                title = tr("A small moment for yourself"),
                body = tr("Use daily check-ins to record your mood, stress, energy, and any reflection you choose to write. There is no perfect answer, and you can keep your reflection brief.")
            )
        }
        item {
            AboutSection(
                title = tr("Notice patterns, not labels"),
                body = tr("Your mood journey and insights help you look back at your own check-ins and notice possible patterns. They are reflective tools, not a diagnosis, clinical assessment, or substitute for professional care.")
            )
        }
        item {
            AboutSection(
                title = tr("A companion when you need to talk"),
                body = tr("The Companion offers a place to put thoughts into words and explore gentle reflections. It can make mistakes and should not replace a qualified professional, especially when you need urgent or specialised support.")
            )
        }
        item {
            AboutSection(
                title = tr("Your pace. Your space."),
                body = tr("You decide what to share and when. MindTrack is intended to support self-awareness with a calm, non-judgmental experience — not to tell you how you should feel.")
            )
        }
    }
}

@Composable
internal fun AboutJourneyDiagram() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)
    ) {
        Text(tr("How MindTrack works"), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(tr("A simple loop — no scores to chase, no pressure to be a certain way."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        listOf(
            Triple("01", tr("Check in"), tr("Name your mood and rate stress and energy.")),
            Triple("02", tr("Reflect"), tr("Add a note if you want; skipping is always okay.")),
            Triple("03", tr("Notice patterns"), tr("Review your own history over time."))
        ).forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(SurfaceSelected), contentAlignment = Alignment.Center) {
                        Text(step.first, color = Lavender, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (index < 2) Box(Modifier.padding(vertical = 3.dp).width(2.dp).height(24.dp).background(Lavender.copy(alpha = 0.35f)))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(top = 2.dp)) {
                    Text(step.second, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(step.third, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            if (index < 2) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun AboutMoodScaleFigure() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)
    ) {
        Text(tr("Your mood is information, not a verdict"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(tr("A mood check-in captures one moment. It does not define you, predict your future, or diagnose a condition."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            moodOptions.reversed().forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    MoodFace(score = option.score, selected = false, modifier = Modifier.size(31.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(tr(option.label), color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF8B9CCB), Lavender, Color(0xFFB9A6D8))),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("All feelings are welcome here."), color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun HomeQuickTools(
    onCheckInClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onResourceClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onExercisesClick: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("A little support, when you need it"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("Choose a small next step."), color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickToolCard(tr("Take a pause"), tr("Check in with yourself"), "◷", Modifier.weight(1f), onCheckInClick)
            QuickToolCard(tr("Explore patterns"), tr("See your mood journey"), "↗", Modifier.weight(1f), onInsightsClick)
        }
        QuickToolCard(tr("Breathe and ground"), tr("Short calming exercises"), "◌", Modifier.fillMaxWidth(), onExercisesClick)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickToolCard(tr("Your history"), tr("Browse and edit past check-ins"), "☰", Modifier.weight(1f), onHistoryClick)
            QuickToolCard(tr("Trusted resources"), tr("Guidance and helplines"), "＋", Modifier.weight(1f), onResourceClick)
        }
    }
}

@Composable
internal fun QuickToolCard(title: String, subtitle: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(press)
            .clip(RoundedCornerShape(19.dp))
            .background(SurfaceColor)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(15.dp)
    ) {
        Text(symbol, color = Lavender, fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.height(9.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
internal fun ProfileStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceElevated).padding(14.dp)) {
        Text(value, color = Lavender, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
internal fun AboutSection(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceColor).padding(20.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
