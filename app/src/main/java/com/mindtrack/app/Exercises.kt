package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Logic (pure, unit tested) ---------------------------------------------------------------

internal enum class BreathPhase(val label: String, val growing: Boolean?) {
    IN("Breathe in", true),
    HOLD_FULL("Hold", null),
    OUT("Breathe out", false),
    HOLD_EMPTY("Rest", null)
}

internal class BreathingPattern(
    val name: String,
    val description: String,
    val steps: List<Pair<BreathPhase, Int>>,     // phase and its length in seconds
    val intro: String                            // read aloud before the first breath
) {
    val cycleSeconds: Int get() = steps.sumOf { it.second }
}

internal val breathingPatterns = listOf(
    BreathingPattern(
        "Box breathing", "Four even steps. Steadying when your mind is racing.",
        listOf(BreathPhase.IN to 4, BreathPhase.HOLD_FULL to 4, BreathPhase.OUT to 4, BreathPhase.HOLD_EMPTY to 4),
        "Sit comfortably and let your shoulders drop. We'll breathe in for four, hold for four, breathe out for four, and rest for four. Follow the circle. Let's begin."
    ),
    BreathingPattern(
        "4-7-8 breathing", "A long, slow out-breath. Helpful for winding down before sleep.",
        listOf(BreathPhase.IN to 4, BreathPhase.HOLD_FULL to 7, BreathPhase.OUT to 8),
        "Sit comfortably. We'll breathe in for four, hold for seven, and breathe out slowly for eight. If holding feels hard, hold for less time. Let's begin."
    ),
    BreathingPattern(
        "Calm breathing", "In for four, out for six. Gentle and easy to keep up.",
        listOf(BreathPhase.IN to 4, BreathPhase.OUT to 6),
        "Sit comfortably. Breathe in gently for four, and out for six. Let's begin."
    )
)

/** [index] counts every phase since the start, so a change in it means a new phase has begun. */
internal data class BreathState(val phase: BreathPhase, val secondsLeft: Int, val size: Float, val index: Int = 0)

/** Where in the pattern we are after [elapsedMillis]. [size] runs from 0 (empty) to 1 (full lungs). */
internal fun breathStateAt(pattern: BreathingPattern, elapsedMillis: Long): BreathState {
    val cycleMs = pattern.cycleSeconds * 1000L
    var t = elapsedMillis % cycleMs
    val cycle = (elapsedMillis / cycleMs).toInt()
    var size = 0f
    for ((stepIndex, step) in pattern.steps.withIndex()) {
        val (phase, seconds) = step
        val ms = seconds * 1000L
        if (t < ms) {
            val progress = t.toFloat() / ms
            val s = when (phase.growing) {
                true -> progress
                false -> 1f - progress
                null -> if (phase == BreathPhase.HOLD_FULL) 1f else 0f
            }
            return BreathState(phase, ((ms - t + 999) / 1000).toInt(), s, cycle * pattern.steps.size + stepIndex)
        }
        t -= ms
        size = when (phase.growing) { true -> 1f; false -> 0f; null -> size }
    }
    return BreathState(pattern.steps.first().first, pattern.steps.first().second, 0f)
}

internal class GuidedStep(val title: String, val body: String)

internal val groundingSteps = listOf(
    GuidedStep("5 things you can see", "Look around slowly. Name five things you can see, and notice one small detail about each."),
    GuidedStep("4 things you can feel", "Notice four things you can touch or feel: your feet on the floor, the chair under you, the fabric on your skin."),
    GuidedStep("3 things you can hear", "Listen. Name three sounds, near or far, without judging them."),
    GuidedStep("2 things you can smell", "Find two scents around you. If nothing stands out, remember two smells you like."),
    GuidedStep("1 thing you can taste", "Notice one taste in your mouth, or think of one taste that comforts you.")
)

internal val relaxationSteps = listOf(
    GuidedStep("Hands and forearms", "Make fists and squeeze for five seconds. Then let go and notice the difference."),
    GuidedStep("Shoulders", "Lift your shoulders towards your ears, hold for five seconds, then let them drop."),
    GuidedStep("Face and jaw", "Scrunch your face, then release. Let your jaw hang loose and your tongue rest."),
    GuidedStep("Chest and belly", "Take a slow breath in and hold it for a moment. Let it out and let your belly soften."),
    GuidedStep("Legs", "Press your heels down and tighten your thighs for five seconds. Then release completely."),
    GuidedStep("Feet", "Curl your toes, hold for five seconds, then let them go. Notice your whole body resting.")
)

// ---- Screens ---------------------------------------------------------------------------------

private enum class ExerciseKind { BREATHING, GROUNDING, RELAXATION }

@Composable
internal fun ExercisesScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val narrator = rememberNarrator()
    var voiceOn by remember { mutableStateOf(voiceGuidanceOn(context)) }
    var kind by remember { mutableStateOf<ExerciseKind?>(null) }
    var pattern by remember { mutableStateOf(breathingPatterns.first()) }

    val voiceBar: @Composable () -> Unit = {
        VoiceBar(narrator = narrator, on = voiceOn, onChange = {
            voiceOn = it
            setVoiceGuidance(context, it)
            if (!it) narrator.stop()
        })
    }
    fun closeSession() { narrator.stop(); kind = null }

    when (kind) {
        null -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Background),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text(tr("←  Back to MindTrack"), color = Lavender) }
                ScreenHeader(
                    eyebrow = tr("A MOMENT TO CALM DOWN"),
                    title = tr("Breathe and ground"),
                    subtitle = tr("Short exercises you can do anywhere. Stop whenever you like.")
                )
                Spacer(Modifier.height(14.dp))
                voiceBar()
                Spacer(Modifier.height(6.dp))
            }
            breathingPatterns.forEach { p ->
                item {
                    ExerciseCard(tr(p.name), tr(p.description), "◌", exercisePurpose(p.name)) { pattern = p; kind = ExerciseKind.BREATHING }
                }
            }
            item {
                ExerciseCard(tr("5-4-3-2-1 grounding"), tr("Use your senses to come back to the present moment."), "✧", exercisePurpose("5-4-3-2-1 grounding")) { kind = ExerciseKind.GROUNDING }
            }
            item {
                ExerciseCard(tr("Body relaxation"), tr("Tense and release each part of your body in turn."), "☾", exercisePurpose("Body relaxation")) { kind = ExerciseKind.RELAXATION }
            }
            item {
                Text(
                    tr("These exercises are for relaxation, not treatment. If you feel dizzy, breathe normally. If you're struggling, the helplines in the Resource centre are there for you."),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        ExerciseKind.BREATHING -> BreathingSession(pattern, narrator, voiceOn, voiceBar, onClose = ::closeSession)
        ExerciseKind.GROUNDING -> StepSession(tr("5-4-3-2-1 grounding"), groundingSteps, narrator, voiceOn, voiceBar, onClose = ::closeSession)
        ExerciseKind.RELAXATION -> StepSession(tr("Body relaxation"), relaxationSteps, narrator, voiceOn, voiceBar, onClose = ::closeSession)
    }
}

/** What each exercise is good for, in plain words. Wellbeing guidance, not treatment. */
internal fun exercisePurpose(name: String): String = when (name) {
    "Box breathing" -> tr("When you feel overwhelmed, your thoughts are racing, or something stressful is coming up. The even rhythm gives your mind something steady to follow.")
    "4-7-8 breathing" -> tr("When you want to wind down before sleep or settle restless energy. The long, slow out-breath helps your body relax. Hold for less time if it feels strained.")
    "Calm breathing" -> tr("For everyday stress and a quick reset. Breathing out for longer than you breathe in is a gentle way to slow down, and you can do it anywhere.")
    "5-4-3-2-1 grounding" -> tr("When you feel anxious or your thoughts keep spiralling. Naming what you can see, touch, hear, smell and taste brings your attention back to the present.")
    "Body relaxation" -> tr("When you feel tense, tired, or can't switch off. Tensing and then releasing each part of your body helps you notice where you hold stress.")
    else -> ""
}

/** Little pixel pictures for the exercises: a square that breathes, an eye, and a figure at rest. */
private val exerciseGlyphs: Map<String, List<String>> = mapOf(
    "breath" to listOf(".........", "..#####..", ".#.....#.", ".#.###.#.", ".#.###.#.", ".#.###.#.", ".#.....#.", "..#####..", "........."),
    "ground" to listOf(".........", ".........", "..#####..", ".#.###.#.", "#.##.##.#", ".#.###.#.", "..#####..", ".........", "........."),
    "relax" to listOf(".........", "..##.....", ".####....", ".####....", "..##.....", "#########", ".#######.", "..#####..", ".........")
)

@Composable
private fun ExerciseGlyph(kind: String) {
    val rows = exerciseGlyphs.getValue(kind)
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val unit = kotlin.math.floor(size.width / 9f).coerceAtLeast(1f)
        val origin = (size.width - unit * 9f) / 2f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') drawRect(Lavender, Offset(origin + x * unit, origin + y * unit), androidx.compose.ui.geometry.Size(unit, unit))
            }
        }
    }
}

@Composable
private fun ExerciseCard(title: String, description: String, symbol: String, purpose: String = "", onClick: () -> Unit) {
    val glyph = when (symbol) { "✧" -> "ground"; "☾" -> "relax"; else -> "breath" }
    HomeSection {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clearAndSetSemantics {}) { ExerciseGlyph(glyph) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(description, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Text("›", color = Lavender, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
            }
            if (purpose.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(tr("GOOD FOR"), color = Lavender, style = PixelLabelStyle.copy(fontSize = 10.sp))
                Spacer(Modifier.height(4.dp))
                Text(purpose, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun BreathingSession(
    pattern: BreathingPattern,
    narrator: Narrator,
    voiceOn: Boolean,
    voiceBar: @Composable () -> Unit,
    onClose: () -> Unit
) {
    val lengths = listOf(1, 3, 5)
    var minutes by remember { mutableIntStateOf(3) }
    var running by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }      // the introduction is being read
    var finished by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    // If the voice never reports back, don't leave the person waiting.
    LaunchedEffect(preparing) {
        if (preparing) {
            kotlinx.coroutines.delay(25_000)
            if (preparing) { preparing = false; running = true }
        }
    }
    // A short spoken cue at the start of every breath phase.
    val phaseNow = breathStateAt(pattern, elapsedMs)
    LaunchedEffect(phaseNow.index, running, voiceOn) {
        if (running && voiceOn) narrator.speak(tr(phaseNow.phase.label))
    }
    LaunchedEffect(finished) {
        if (finished && voiceOn) narrator.speak(tr("Well done. Take a moment to notice how you feel."))
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { narrator.stop() } }

    LaunchedEffect(running, minutes) {
        if (!running) return@LaunchedEffect
        val startOffset = elapsedMs
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            elapsedMs = startOffset + (now - start) / 1_000_000
            if (elapsedMs >= minutes * 60_000L) {
                running = false
                finished = true
                break
            }
        }
    }

    val state = breathStateAt(pattern, elapsedMs)

    Column(
        Modifier.fillMaxSize().background(Background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onClose) { Text(tr("←  Back"), color = Lavender) }
        }
        Text(tr(pattern.name).uppercase(), color = Lavender, style = PixelLabelStyle)
        Spacer(Modifier.height(6.dp))
        Text(tr(pattern.description), color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
        Spacer(Modifier.height(12.dp))
        voiceBar()

        Spacer(Modifier.weight(1f))

        BreathingMoon(
            size = state.size,
            running = running,
            finished = finished,
            phaseLabel = if (running || elapsedMs > 0 && !finished) tr(state.phase.label) else null,
            secondsLeft = state.secondsLeft,
            idleLabel = when {
                preparing -> tr("Listen…")
                finished -> tr("Well done")
                else -> tr("Ready?")
            }
        )

        Spacer(Modifier.height(18.dp))
        // Time left, as a row of little blocks that fill in.
        val total = minutes * 60_000f
        val blocks = 24
        val filled = ((elapsedMs / total) * blocks).toInt().coerceIn(0, blocks)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(blocks) { i ->
                Box(Modifier.size(width = 8.dp, height = 8.dp).background(if (i < filled) Lavender else Border))
            }
        }

        Spacer(Modifier.weight(1f))

        if (!running && !preparing) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                lengths.forEach { m ->
                    val selected = minutes == m
                    Column(
                        Modifier
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { minutes = m; elapsedMs = 0; finished = false })
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(trf("%1\$d min", m), color = if (selected) Lavender else TextSecondary, style = PixelLabelStyle.copy(fontSize = 13.sp))
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(width = 26.dp, height = 3.dp).background(if (selected) Lavender else Color.Transparent))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                when {
                    running -> { running = false; narrator.stop() }
                    else -> {
                        if (finished) { elapsedMs = 0; finished = false }
                        if (voiceOn && narrator.status == VoiceStatus.READY && elapsedMs == 0L) {
                            // Read the introduction first, then begin the breathing.
                            preparing = true
                            narrator.speakThen(tr(pattern.intro)) { if (preparing) { preparing = false; running = true } }
                        } else running = true
                    }
                }
            },
            enabled = !preparing,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
        ) {
            Text(
                when {
                    preparing -> tr("Starting…")
                    running -> tr("Pause")
                    finished -> tr("Again")
                    elapsedMs > 0 -> tr("Resume")
                    else -> tr("Start")
                },
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StepSession(
    title: String,
    steps: List<GuidedStep>,
    narrator: Narrator,
    voiceOn: Boolean,
    voiceBar: @Composable () -> Unit,
    onClose: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex

    // Read each step aloud when it appears.
    LaunchedEffect(index, voiceOn, narrator.status) {
        if (voiceOn) narrator.speak(tr(step.title) + ". " + tr(step.body))
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { narrator.stop() } }

    Column(Modifier.fillMaxSize().background(Background).padding(20.dp)) {
        TextButton(onClick = onClose) { Text(tr("←  Back"), color = Lavender) }
        Text(title.uppercase(), color = Lavender, style = PixelLabelStyle)
        Spacer(Modifier.height(10.dp))
        voiceBar()
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.indices.forEach { i ->
                Box(Modifier.size(width = 22.dp, height = 8.dp).background(if (i <= index) Lavender else Border))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth()) {
            PixelMoon(size = 44.dp, mood = if (last) MoonMood.HAPPY else MoonMood.SLEEPY, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.height(16.dp))
            Text(trf("Step %1\$d of %2\$d", index + 1, steps.size).uppercase(), color = Lavender, style = PixelLabelStyle)
            Spacer(Modifier.height(8.dp))
            Text(tr(step.title), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(tr(step.body), color = TextSecondary, fontSize = 16.sp, lineHeight = 25.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index > 0) {
                TextButton(onClick = { index-- }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(tr("Previous"), color = TextSecondary)
                }
            }
            Button(
                onClick = { if (last) onClose() else index++ },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(if (last) tr("Finish") else tr("Next"), fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun VoiceBar(narrator: Narrator, on: Boolean, onChange: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().quietCard(12.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("VOICE GUIDANCE"), color = if (on) Lavender else TextSecondary, style = PixelLabelStyle)
            androidx.compose.material3.Switch(
                checked = on, onCheckedChange = onChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Background)
            )
        }
        if (on) when (narrator.status) {
            VoiceStatus.MISSING_DATA -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("A voice for this language isn't installed on your phone."), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { installVoiceData(context) }) { Text(tr("Install voice"), color = Lavender, fontSize = 12.sp) }
            }
            VoiceStatus.UNAVAILABLE -> Text(tr("Voice guidance isn't available on this phone for this language."), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            else -> Unit
        }
    }
}

/** Cells of a pixel-drawn ring, on a grid 25 wide, for a ring of radius [r] cells. */
private fun ringCells(r: Float): List<Pair<Int, Int>> = buildList {
    val c = 12
    for (y in 0..24) for (x in 0..24) {
        val d = Math.hypot((x - c).toDouble(), (y - c).toDouble())
        if (d >= r - 0.6 && d < r + 0.5) add(x to y)
    }
}

/**
 * The little moon breathes: it sits inside a ring of pixels that steps outwards as you breathe in and back in as
 * you breathe out, with the phase and the seconds left written underneath. With animations off it simply shows the
 * current step, so it works the same either way.
 */
@Composable
private fun BreathingMoon(
    size: Float,
    running: Boolean,
    finished: Boolean,
    phaseLabel: String?,
    secondsLeft: Int,
    idleLabel: String
) {
    val ring = Lavender
    // Quantise to whole ring sizes so it steps like a sprite: radius 6 (empty lungs) up to 11 (full).
    val radius = 6f + kotlin.math.round(size.coerceIn(0f, 1f) * 5f)
    val cells = remember(radius) { ringCells(radius) }
    val inner = remember(radius) { ringCells(radius - 1.6f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = kotlin.math.floor(this.size.width / 25f).coerceAtLeast(1f)
                val origin = (this.size.width - unit * 25f) / 2f
                val cellSize = androidx.compose.ui.geometry.Size(unit, unit)
                inner.forEach { (x, y) ->
                    drawRect(ring.copy(alpha = 0.20f), Offset(origin + x * unit, origin + y * unit), cellSize)
                }
                cells.forEach { (x, y) ->
                    drawRect(ring, Offset(origin + x * unit, origin + y * unit), cellSize)
                }
            }
            PixelMoon(size = 84.dp, mood = if (finished) MoonMood.HAPPY else MoonMood.SLEEPY, animated = !running)
        }
        Spacer(Modifier.height(10.dp))
        if (phaseLabel != null) {
            Text(phaseLabel.uppercase(), color = TextPrimary, style = PixelLabelStyle.copy(fontSize = 16.sp, letterSpacing = 3.sp))
            Spacer(Modifier.height(4.dp))
            Text("$secondsLeft", color = Lavender, style = PixelLabelStyle.copy(fontSize = 34.sp, letterSpacing = 0.sp), fontWeight = FontWeight.Bold)
        } else {
            Text(idleLabel.uppercase(), color = TextPrimary, style = PixelLabelStyle.copy(fontSize = 16.sp, letterSpacing = 3.sp))
        }
    }
}
