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
// PERSONALIZED INSIGHT LOGIC
// ------------------------------------------------------------

internal fun personalizedInsight(
    records: List<CheckInRecord>
): String {
    if (records.isEmpty()) {
        return tr("Every check-in is a small act of self-awareness. Start whenever you're ready.")
    }

    if (records.size == 1) {
        return tr("You've recorded your first check-in. Keep checking in to discover how your mood, stress, and energy change over time.")
    }

    val ordered = records.sortedBy { it.date }
    val averageMood = ordered.map { it.mood }.average()
    val averageStress = ordered.map { it.stress }.average()
    val averageEnergy = ordered.map { it.energy }.average()
    val latest = ordered.last()
    val moodLabel = moodOptions.firstOrNull { it.score == latest.mood }?.label?.let { tr(it).lowercase(Locale.getDefault()) } ?: tr("mixed")
    val firstHalf = ordered.take((ordered.size / 2).coerceAtLeast(1))
    val secondHalf = ordered.drop(ordered.size / 2).ifEmpty { ordered.takeLast(1) }
    val moodChange = secondHalf.map { it.mood }.average() - firstHalf.map { it.mood }.average()
    val trendSentence = when {
        ordered.size < 3 -> tr(" With a few more check-ins, we'll be able to compare how things shift over time.")
        moodChange >= 0.6 -> tr(" Your more recent mood ratings are higher than your earlier ones. It may be worth noticing what has helped — without assuming one thing caused it.")
        moodChange <= -0.6 -> tr(" Your more recent mood ratings are lower than your earlier ones. Consider giving yourself a little extra support and reflecting on what's been different.")
        else -> tr(" Your mood ratings have been fairly steady across these check-ins, with room for day-to-day variation.")
    }

    fun one(v: Double) = String.format(Locale.getDefault(), "%.1f", v)
    val mainObservation = when {
        averageStress >= 4.0 -> trf("Stress has been the strongest signal in your recent check-ins (averaging %1\$s/5). A small pause or talking with someone you trust may be a useful next step.", one(averageStress))
        averageEnergy <= 2.5 -> trf("Your energy has averaged %1\$s/5 recently. It could help to notice how sleep, workload, and breaks line up with your energy — this is a pattern to explore, not a diagnosis.", one(averageEnergy))
        averageMood >= 4.0 -> trf("Your mood has averaged %1\$s/5, and your latest check-in was %2\$s. What has been supporting you lately may be worth making a little more room for.", one(averageMood), moodLabel)
        averageMood <= 2.5 -> trf("Your mood has averaged %1\$s/5, and your latest check-in was %2\$s. You don't have to handle a difficult stretch alone; consider reaching out to someone you trust if that feels right.", one(averageMood), moodLabel)
        else -> trf("Your latest check-in was %1\$s. Your mood, stress, and energy don't have to move together, so look at each as its own signal rather than a single overall score.", moodLabel)
    }
    return mainObservation + trendSentence
}
