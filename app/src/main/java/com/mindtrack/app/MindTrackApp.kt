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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import android.net.Network
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.statusBarsPadding
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
// APP STATE AND LOCAL STORAGE
// ------------------------------------------------------------

@Composable
internal fun MindTrackApp(
    uid: String,
    quickMood: Int? = null,
    onQuickMoodConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as android.app.Application

    // Keyed by account, so a different person signing in never sees someone else's in-memory state.
    val vm: MindTrackViewModel = viewModel(
        key = "app-$uid",
        factory = viewModelFactory { initializer { MindTrackViewModel(application, uid) } }
    )
    val companionVm: CompanionViewModel = viewModel(
        key = "companion-$uid",
        factory = viewModelFactory { initializer { CompanionViewModel(application, uid) } }
    )
    val accountVm: AccountViewModel = viewModel(key = "account-$uid")
    val suggestionsVm: SuggestionsViewModel = viewModel(
        key = "suggestions-$uid",
        factory = viewModelFactory { initializer { SuggestionsViewModel(application, uid) } }
    )

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showResourceCentre by remember { mutableStateOf(false) }
    var showAboutMindTrack by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showExercises by remember { mutableStateOf(false) }
    var selectedMoodForDetails by remember { mutableStateOf<MoodOption?>(null) }

    LaunchedEffect(quickMood) {
        if (quickMood != null) {
            vm.mood = quickMood
            selectedTab = AppTab.MOOD
            showResourceCentre = false
            showAboutMindTrack = false
            showHistory = false
            onQuickMoodConsumed()
        }
    }

    // Retry unsent changes whenever the network comes back, and start a fresh day after midnight.
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                coroutineScope.launch { vm.syncPending() }
            }
        }
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.onResume()
                companionVm.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Box(modifier = Modifier.weight(1f).statusBarsPadding()) {
                val screenKey = when {
                    showResourceCentre -> "resources"
                    showAboutMindTrack -> "about"
                    showHistory -> "history"
                    showPrivacy -> "privacy"
                    showExercises -> "exercises"
                    else -> selectedTab.name
                }
                Crossfade(
                    targetState = screenKey,
                    animationSpec = tween(durationMillis = 180),
                    label = "screenSwitch"
                ) { key ->
                    when (key) {
                        "resources" -> ResourceCentreScreen(onBack = { showResourceCentre = false })
                        "about" -> AboutMindTrackScreen(onBack = { showAboutMindTrack = false })
                        "privacy" -> PrivacyPolicyScreen(onBack = { showPrivacy = false })
                        "exercises" -> ExercisesScreen(onBack = { showExercises = false })
                        "history" -> HistoryScreen(
                            records = vm.allRecords,
                            reportName = accountVm.displayName,
                            onBack = { showHistory = false },
                            onEdit = { date, note, tags -> vm.editRecord(date, note, tags) },
                            onDelete = { vm.deleteRecord(it) },
                            onBackfill = { date, mood, stress, energy, note, tags -> vm.backfill(date, mood, stress, energy, note, tags) }
                        )
                        "HOME" -> HomeScreen(
                            today = LocalDate.now(),
                            mood = vm.mood,
                            checkedIn = vm.checkedIn,
                            records = vm.recentRecords,
                            streak = vm.streak,
                            onHistoryClick = { showHistory = true },
                            onExercisesClick = { showExercises = true },
                            onCheckInClick = { selectedTab = AppTab.MOOD },
                            onInsightsClick = { selectedTab = AppTab.INSIGHTS },
                            onResourceClick = { showResourceCentre = true },
                            onAboutClick = { showAboutMindTrack = true },
                            onMoodOptionClick = { selectedMoodForDetails = it }
                        )

                        "MOOD" -> MoodScreen(
                            mood = vm.mood,
                            stress = vm.stress,
                            energy = vm.energy,
                            journalState = vm.journal,
                            tags = vm.tags,
                            checkedIn = vm.checkedIn,
                            isSyncing = vm.isSyncing,
                            syncMessage = vm.syncMessage,
                            syncSuccess = vm.syncOk,
                            onMoodChange = { vm.mood = it },
                            onStressChange = { vm.stress = it },
                            onEnergyChange = { vm.energy = it },
                            onToggleTag = { vm.toggleTag(it) },
                            onSave = {
                                vm.saveToday()
                                selectedTab = AppTab.HOME
                            }
                        )

                        "INSIGHTS" -> InsightsScreen(
                            records = vm.recentRecords,
                            suggestions = suggestionsVm,
                            allRecords = vm.allRecords,
                            checkedInToday = vm.checkedIn,
                            weekly = vm.weekly,
                            dismissedInsights = vm.dismissed,
                            onDismissInsight = { vm.dismissInsight(it) }
                        )

                        "COMPANION" -> CompanionScreen(
                            vm = companionVm,
                            records = vm.recentRecords,
                            streak = vm.streak,
                            onOpenExercises = { showExercises = true }
                        )

                        "PROFILE" -> ProfileScreen(
                            records = vm.recentRecords,
                            checkedInToday = vm.checkedIn,
                            streak = vm.streak,
                            totalCheckIns = vm.allRecords.size,
                            onHistoryClick = { showHistory = true },
                            onInsightsClick = { selectedTab = AppTab.INSIGHTS },
                            account = accountVm,
                            memoryCount = companionVm.memoryCount,
                            memorySince = companionVm.memorySince,
                            onClearMemory = { companionVm.clearMemory() },
                            onPrivacyClick = { showPrivacy = true },
                            onDeleteAccount = { password -> vm.deleteAccount { vm.reauthenticateWithPassword(password) } },
                            onDeleteAccountWithGoogle = {
                                val activity = context as? android.app.Activity
                                if (activity == null) tr("Couldn't open Google sign-in.")
                                else vm.deleteAccount { accountVm.reauthenticateWithGoogle(activity) }
                            },
                            onSignOut = {
                                // Drop everything held in memory for this person before the session ends.
                                companionVm.wipe()
                                vm.wipe()
                                vm.signOut()
                            }
                        )
                        else -> Unit
                    }
                }
            }

            if (!showResourceCentre && !showAboutMindTrack && !showHistory && !showPrivacy && !showExercises && !imeVisible) {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }

        if (vm.showSupport) SupportDialog(onDismiss = { vm.showSupport = false })

        selectedMoodForDetails?.let { option ->
            MoodDetailDialog(
                option = option,
                onDismiss = { selectedMoodForDetails = null },
                onContinue = {
                    vm.mood = option.score
                    selectedMoodForDetails = null
                    selectedTab = AppTab.MOOD
                    showResourceCentre = false
                    showAboutMindTrack = false
                    showHistory = false
                }
            )
        }
    }
}

@Composable
internal fun MoodFace(score: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val faceColor = if (selected) Lavender else Lavender.copy(alpha = 0.82f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        drawCircle(color = faceColor.copy(alpha = if (selected) 0.20f else 0.11f), radius = w * 0.46f, center = center)
        drawCircle(color = faceColor, radius = w * 0.43f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.045f))
        val eyeY = h * 0.40f
        drawCircle(color = faceColor, radius = w * 0.045f, center = Offset(w * 0.35f, eyeY))
        drawCircle(color = faceColor, radius = w * 0.045f, center = Offset(w * 0.65f, eyeY))
        val mouth = Path()
        when (score) {
            5 -> { mouth.moveTo(w * 0.28f, h * 0.56f); mouth.cubicTo(w * 0.38f, h * 0.78f, w * 0.62f, h * 0.78f, w * 0.72f, h * 0.56f) }
            4 -> { mouth.moveTo(w * 0.32f, h * 0.59f); mouth.cubicTo(w * 0.42f, h * 0.72f, w * 0.58f, h * 0.72f, w * 0.68f, h * 0.59f) }
            3 -> { mouth.moveTo(w * 0.34f, h * 0.62f); mouth.lineTo(w * 0.66f, h * 0.62f) }
            2 -> { mouth.moveTo(w * 0.31f, h * 0.70f); mouth.cubicTo(w * 0.42f, h * 0.54f, w * 0.58f, h * 0.54f, w * 0.69f, h * 0.70f) }
            else -> {
                mouth.moveTo(w * 0.30f, h * 0.70f); mouth.cubicTo(w * 0.40f, h * 0.52f, w * 0.60f, h * 0.52f, w * 0.70f, h * 0.70f)
                drawLine(faceColor, Offset(w * 0.27f, h * 0.28f), Offset(w * 0.42f, h * 0.32f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
                drawLine(faceColor, Offset(w * 0.58f, h * 0.32f), Offset(w * 0.73f, h * 0.28f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
            }
        }
        drawPath(path = mouth, color = faceColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.055f, cap = StrokeCap.Round))
    }
}

@Composable
internal fun MoodDetailDialog(option: MoodOption, onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodFace(score = option.score, selected = true, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(trf("Feeling %1\$s", tr(option.label).lowercase()), color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(tr(option.description), color = Lavender, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column {
                Text(tr(option.meaning), color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(16.dp))
                Text(tr("A gentle reflection"), color = Lavender, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text(tr(option.prompt), color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                Text(tr("You can skip the reflection. There's no right or wrong way to feel."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = {
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)) {
                Text(tr("Continue to check-in"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Not now"), color = TextSecondary) }
        }
    )
}

@Composable
internal fun ResourceCentreScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    data class Resource(val category: String, val title: String, val description: String, val url: String)
    val resources = listOf(
        Resource(tr("GLOBAL HEALTH"), tr("World Health Organization"), tr("Global guidance on mental health, wellbeing, and support."), "https://www.who.int/health-topics/mental-health"),
        Resource(tr("MENTAL HEALTH EDUCATION"), tr("National Institute of Mental Health"), tr("Understand mental health conditions, treatment, and recovery."), "https://www.nimh.nih.gov/health"),
        Resource(tr("PRACTICAL SUPPORT"), tr("NHS mental health"), tr("Information on common concerns and ways to find help."), "https://www.nhs.uk/mental-health/"),
        Resource(tr("INDIA · TELE-MANAS"), tr("National mental health helpline"), tr("For mental health support in India, call 14416. If you are in immediate danger, contact local emergency services."), "https://telemanas.mohfw.gov.in/")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text(tr("←  Back to MindTrack"), color = Lavender) }
            ScreenHeader(eyebrow = tr("TRUSTED INFORMATION"), title = tr("Resource centre"), subtitle = tr("Clear, reliable information to help you understand your wellbeing and find support."))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceElevated).padding(18.dp)) {
                Text(tr("A note before you begin"), color = Lavender, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(tr("These resources are for education and support, not diagnosis. MindTrack does not replace a qualified mental health professional."), color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceColor).padding(18.dp)) {
                Text(tr("Need to talk to someone right now?"), color = Coral, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(tr("These services are free and confidential. Tap one to call."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                HelplineList()
            }
        }
        items(resources) { resource ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceColor)
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))) }
                    }.padding(18.dp)
            ) {
                Text(resource.category, color = Lavender, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(resource.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(resource.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text(tr("Open official resource  ↗"), color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text(tr("If you or someone else may be in immediate danger, contact local emergency services now."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

internal fun loadRecentRecords(
    prefs: SharedPreferences,
    today: LocalDate,
    numberOfDays: Int
): List<CheckInRecord> {
    return (numberOfDays - 1 downTo 0).mapNotNull { offset ->
        val date = today.minusDays(offset.toLong())
        val key = date.toString()

        if (!prefs.getBoolean("checkin_$key", false)) {
            null
        } else {
            CheckInRecord(
                date = key,
                mood = prefs.getInt("mood_$key", 3),
                stress = prefs.getInt("stress_$key", 3),
                energy = prefs.getInt("energy_$key", 3),
                journal = prefs.getString("journal_$key", "") ?: "",
                tags = readTags(prefs, key)
            )
        }
    }
}

internal fun countSavedCheckIns(
    prefs: SharedPreferences
): Int {
    return prefs.all.keys.count { key ->
        key.startsWith("checkin_") &&
                key != "checkin_date" &&
                prefs.getBoolean(key, false)
    }
}
