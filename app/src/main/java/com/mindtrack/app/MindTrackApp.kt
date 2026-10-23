package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Size
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
                            allRecords = vm.allRecords,
                            dismissedInsights = vm.dismissed,
                            onInsightsClick = { selectedTab = AppTab.INSIGHTS },
                            name = accountVm.displayName,
                            onHistoryClick = { showHistory = true },
                            onExercisesClick = { showExercises = true },
                            onCheckInClick = { selectedTab = AppTab.MOOD },
                            onResourcesClick = { showResourceCentre = true },
                            onGuideClick = { showAboutMindTrack = true }
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
    }
}

/** Pixel faces for the five moods, drawn on an 11 by 11 grid so they match the little moon. */
private const val FACE_GRID = 11

private val faceDisc: List<Triple<Int, Int, Double>> = buildList {
    for (y in 0 until FACE_GRID) for (x in 0 until FACE_GRID) {
        add(Triple(x, y, Math.hypot(x - 5.0, y - 5.0)))
    }
}

/** Eye and mouth pixels for a mood score from 1 (Rough) to 5 (Great). Eyes are two pixels tall so they read at small sizes. */
internal fun faceFeatures(score: Int): List<Pair<Int, Int>> = when (score) {
    5 -> listOf(2 to 4, 3 to 3, 4 to 4, 6 to 4, 7 to 3, 8 to 4, 3 to 6, 4 to 7, 5 to 7, 6 to 7, 7 to 6)
    4 -> listOf(3 to 3, 3 to 4, 7 to 3, 7 to 4, 3 to 6, 4 to 7, 5 to 7, 6 to 7, 7 to 6)
    3 -> listOf(3 to 3, 3 to 4, 7 to 3, 7 to 4, 4 to 7, 5 to 7, 6 to 7)
    2 -> listOf(3 to 3, 3 to 4, 7 to 3, 7 to 4, 3 to 8, 4 to 7, 5 to 7, 6 to 7, 7 to 8)
    else -> listOf(2 to 2, 3 to 3, 3 to 5, 7 to 5, 8 to 2, 7 to 3, 3 to 8, 4 to 7, 5 to 7, 6 to 7, 7 to 8)
}

@Composable
internal fun MoodFace(score: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val ink = if (selected) Lavender else Lavender.copy(alpha = 0.82f)
    val features = remember(score) { faceFeatures(score) }
    Canvas(modifier = modifier) {
        // Whole-pixel cells, centred, so neighbouring squares meet without seams.
        val cell = kotlin.math.floor(size.width / FACE_GRID).coerceAtLeast(1f)
        val origin = (size.width - cell * FACE_GRID) / 2f
        val pixel = Size(cell, cell)
        if (selected) drawCircle(ink.copy(alpha = 0.16f), radius = cell * 4.7f, center = Offset(origin + cell * 5.5f, origin + cell * 5.5f))
        faceDisc.forEach { (x, y, d) ->
            if (d in 4.15..5.4) drawRect(ink, Offset(origin + x * cell, origin + y * cell), pixel)
        }
        features.forEach { (x, y) -> drawRect(ink, Offset(origin + x * cell, origin + y * cell), pixel) }
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
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text(tr("←  Back to MindTrack"), color = Lavender) }
            PixelMoon(size = 48.dp, mood = MoonMood.SLEEPY, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp).clearAndSetSemantics {})
            ScreenHeader(eyebrow = tr("TRUSTED INFORMATION"), title = tr("Resource centre"), subtitle = tr("Clear, reliable information to help you understand your wellbeing and find support."))
        }
        item {
            HomeSection {
                Text(tr("A note before you begin").uppercase(), color = Lavender, style = PixelLabelStyle)
                Spacer(Modifier.height(8.dp))
                Text(tr("These resources are for education and support, not diagnosis. MindTrack does not replace a qualified mental health professional."), color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
        item {
            HomeSection {
                Text(tr("Need to talk to someone right now?"), color = Coral, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(tr("These services are free and confidential. Tap one to call."), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                HelplineList()
            }
        }
        items(resources) { resource ->
            HomeSection {
              Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))) }
                    }
              ) {
                Text(resource.category, color = Lavender, style = PixelLabelStyle)
                Spacer(Modifier.height(6.dp))
                Text(resource.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(resource.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text(tr("Open official resource  ↗"), color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
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
