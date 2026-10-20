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
// MINDTRACK DESIGN SYSTEM
// ------------------------------------------------------------

internal val Background: Color get() = ThemeState.palette.background
internal val SurfaceColor: Color get() = ThemeState.palette.surface
internal val SurfaceElevated: Color get() = ThemeState.palette.surfaceElevated
internal val SurfaceSelected: Color get() = ThemeState.palette.surfaceSelected
internal val Lavender: Color get() = ThemeState.palette.lavender
internal val LavenderBright: Color get() = ThemeState.palette.lavenderBright
internal val TextPrimary: Color get() = ThemeState.palette.textPrimary
internal val TextSecondary: Color get() = ThemeState.palette.textSecondary
internal val Mint: Color get() = ThemeState.palette.mint
internal val Border: Color get() = ThemeState.palette.border
internal val Coral: Color get() = ThemeState.palette.coral

internal enum class AppTab(
    val label: String,
    val icon: String
) {
    HOME("Home", "⌂"),
    MOOD("Mood", "☾"),
    INSIGHTS("Insights", "▥"),
    COMPANION("Companion", "♡"),
    PROFILE("Profile", "○")
}

internal data class MoodOption(
    val score: Int,
    val emoji: String,
    val label: String,
    val description: String,
    val meaning: String,
    val prompt: String
)

internal val moodOptions = listOf(
    MoodOption(5, "😄", "Great", "Feeling positive", "You feel upbeat, connected, or energised. You don't need to justify a good day — you can simply enjoy it.", "What has been going well for you today?"),
    MoodOption(4, "🙂", "Good", "Doing fairly well", "Things feel mostly okay, even if the day isn't perfect. There may be small moments worth noticing.", "What made today a little better?"),
    MoodOption(3, "😐", "Okay", "Somewhere in between", "You may feel steady, neutral, or unsure how to describe the day. You don't have to force a stronger feeling.", "What has been taking up most of your energy?"),
    MoodOption(2, "😔", "Low", "A difficult day", "You may feel tired, disappointed, disconnected, or weighed down. It's okay to take things one step at a time.", "What would make today feel a little easier?"),
    MoodOption(1, "😣", "Rough", "Things feel heavy", "Things may feel overwhelming right now. You deserve care and support, and you don't have to explain everything at once.", "What do you need most right now — rest, support, or space?")
)

internal data class CheckInRecord(
    val date: String,
    val mood: Int,
    val stress: Int,
    val energy: Int,
    val journal: String,
    val tags: List<String> = emptyList()
)

/** Things a person can tag a day with. Fixed list, so patterns can be compared across days. */
internal val moodTags = listOf(
    "Poor sleep", "Good sleep", "Work or study", "Family", "Friends",
    "Exercise", "Health", "Money", "Alone time", "Outdoors"
)
internal const val MAX_TAGS = 6


/** One colour per mood level, used by the calendar and the PDF so they always agree. */
internal fun moodColorFor(score: Int): Color = when (score) {
    1 -> Color(0xFFD9667A)
    2 -> Color(0xFFE49A6B)
    3 -> Color(0xFFE2C15A)
    4 -> Color(0xFF7FC59A)
    else -> Color(0xFF4DAF86)
}
