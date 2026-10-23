package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
// DAILY MOOD CHECK-IN
// ------------------------------------------------------------

@Composable
internal fun MoodScreen(
    mood: Int,
    stress: Int,
    energy: Int,
    journalState: androidx.compose.runtime.MutableState<String>,
    tags: List<String>,
    checkedIn: Boolean,
    isSyncing: Boolean,
    syncMessage: String?,
    syncSuccess: Boolean,
    onMoodChange: (Int) -> Unit,
    onStressChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 26.dp
        ),
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = tr("A MOMENT FOR YOU"),
                title = tr("Check in with yourself"),
                subtitle = tr("No judgement. No pressure. Just you.")
            )
        }

        item {
            val current = moodOptions.first { it.score == mood }
            Column(Modifier.fillMaxWidth()) {
                Text(tr("What's your mood today?"), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(tr("Choose the feeling that comes closest."), color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelMoon(size = 46.dp, mood = moonMoodFor(mood), modifier = Modifier.clearAndSetSemantics {})
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(current.description), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(tr(current.meaning), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    moodOptions.forEach { option ->
                        val selected = mood == option.score
                        val press = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .pressScale(press)
                                .selectable(
                                    selected = selected,
                                    interactionSource = press,
                                    indication = null,
                                    role = Role.RadioButton
                                ) { onMoodChange(option.score) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(if (selected) Lavender.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        if (selected) 1.5.dp else 1.dp,
                                        if (selected) Lavender else Border,
                                        RoundedCornerShape(15.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                MoodFace(score = option.score, selected = selected, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                tr(option.label),
                                color = if (selected) Lavender else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        item {
            RatingSlider(
                title = tr("Stress level"),
                subtitle = tr("How much pressure are you feeling?"),
                value = stress,
                lowLabel = tr("Calm"),
                highLabel = tr("Very stressed"),
                onValueChange = onStressChange
            )
        }

        item {
            RatingSlider(
                title = tr("Energy level"),
                subtitle = tr("How energised do you feel?"),
                value = energy,
                lowLabel = tr("Drained"),
                highLabel = tr("Energised"),
                onValueChange = onEnergyChange
            )
        }

        item {
            HomeSection {
                Text(tr("What's been on your mind?"), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    trf("Optional. Pick up to %1\$d. Over time they help Insights notice patterns.", MAX_TAGS),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                TagChips(selected = tags, onToggle = onToggleTag)
            }
        }

        item {
            HomeSection {
                Text(tr("Put it into words"), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)

                Spacer(Modifier.height(6.dp))

                Text(tr("Sometimes writing it down helps you understand it."), color = TextSecondary, fontSize = 13.sp)

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = journalState.value,
                    onValueChange = {
                        if (it.length <= 2000) journalState.value = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = {
                        Text(tr("Today, I've been thinking about..."), color = TextSecondary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender,
                        unfocusedBorderColor = Border,
                        cursorColor = Lavender,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    trf("%1\$d/2000 characters", journalState.value.length),
                    color = TextSecondary,
                    style = PixelLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        item {
            Button(
                onClick = onSave,
                enabled = !isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Lavender,
                    contentColor = Background
                )
            ) {
                Text(
                    if (checkedIn) tr("Save updated check-in  ✓")
                    else tr("Save today's check-in  →"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            syncMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (syncSuccess) Mint else TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            } ?: run {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Your check-in is saved locally and can sync to your account."),
                    modifier = Modifier.fillMaxWidth(),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
