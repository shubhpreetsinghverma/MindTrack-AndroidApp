package com.mindtrack.app

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private fun prettyDate(date: String): String =
    runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(appLocale()))
    }.getOrDefault(date)

@Composable
internal fun HistoryScreen(
    records: List<CheckInRecord>,
    reportName: String,
    onBack: () -> Unit,
    onEdit: (date: String, note: String, tags: List<String>) -> Unit,
    onDelete: (date: String) -> Unit,
    onBackfill: (date: LocalDate, mood: Int, stress: Int, energy: Int, note: String, tags: List<String>) -> Unit
) {
    var backfillDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<CheckInRecord?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var calendarView by remember { mutableStateOf(false) }
    val byDate = remember(records) { records.associateBy { it.date } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(tr("←  Back to MindTrack"), color = Lavender) }
                if (records.isNotEmpty()) {
                    TextButton(onClick = { showExport = true }) { Text(tr("Export PDF  ↗"), color = Lavender, fontWeight = FontWeight.SemiBold) }
                }
            }
            ScreenHeader(
                eyebrow = tr("YOUR JOURNEY"),
                title = tr("Check-in history"),
                subtitle = tr("Every check-in you've saved. Tap one to edit its note and tags, or delete it.")
            )
        }

        item {
            // Two plain tabs with an underline, like a cursor under the active one.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(false to tr("List"), true to tr("Calendar")).forEach { (isCalendar, label) ->
                    val selected = calendarView == isCalendar
                    Column(
                        Modifier
                            .selectable(selected = selected, role = Role.Tab, onClick = { calendarView = isCalendar })
                            .padding(vertical = 6.dp)
                    ) {
                        Text(label, color = if (selected) Lavender else TextSecondary, style = PixelLabelStyle.copy(fontSize = 13.sp))
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(width = 26.dp, height = 3.dp).background(if (selected) Lavender else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
        }

        if (records.isEmpty()) {
            item {
                EmptyState(emoji = "✧", title = tr("Nothing here yet"), message = tr("Your saved check-ins will appear here."))
            }
        } else if (calendarView) {
            item { MonthCalendar(byDate = byDate, onPick = { editing = it }, onBackfill = { backfillDate = it }) }
        } else {
            items(records, key = { it.date }) { record ->
                val option = moodOptions.firstOrNull { it.score == record.mood }
                val press = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .pressScale(press)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = press, indication = null,
                            onClickLabel = tr("Edit entry"), role = Role.Button
                        ) { editing = record }
                        .drawBehind { drawLine(Border, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
                        .padding(top = 6.dp, bottom = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoodFace(score = record.mood, selected = true, modifier = Modifier.size(44.dp).clearAndSetSemantics {})
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prettyDate(record.date), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                trf("%1\$s  ·  Stress %2\$d/5  ·  Energy %3\$d/5", tr(option?.label ?: "Mood"), record.stress, record.energy),
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                        Text("✎", color = Lavender, fontSize = 16.sp)
                    }
                    if (record.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(record.tags.joinToString("  ·  ") { tr(it) }, color = Lavender, style = PixelLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.6.sp))
                    }
                    if (record.journal.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(record.journal, color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
            }
        }
    }

    backfillDate?.let { date ->
        BackfillDialog(
            date = date,
            onDismiss = { backfillDate = null },
            onSave = { mood, stress, energy, note, tags -> onBackfill(date, mood, stress, energy, note, tags); backfillDate = null }
        )
    }

    if (showExport) ExportDialog(records = records, name = reportName, onDismiss = { showExport = false })

    editing?.let { record ->
        EditEntryDialog(
            record = record,
            onDismiss = { editing = null },
            onSave = { note, tags -> onEdit(record.date, note, tags); editing = null },
            onDelete = { onDelete(record.date); editing = null }
        )
    }
}

@Composable
private fun MonthCalendar(byDate: Map<String, CheckInRecord>, onPick: (CheckInRecord) -> Unit, onBackfill: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1              // Monday-first grid
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    HomeSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", color = Lavender, fontSize = 22.sp) }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, appLocale())} ${month.year}",
                color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                Text("›", color = if (month < YearMonth.now()) Lavender else TextSecondary, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, color = TextSecondary, style = PixelLabelStyle.copy(fontSize = 11.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNumber)
                            val record = byDate[date.toString()]
                            val isToday = date == today
                            val addable = record == null && canBackfill(date, today, byDate.keys)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (record != null) moodColorFor(record.mood) else androidx.compose.ui.graphics.Color.Transparent)
                                    .then(if (isToday) Modifier.border(1.5.dp, TextPrimary, RoundedCornerShape(8.dp)) else Modifier)
                                    .then(if (addable) Modifier.border(1.dp, Lavender.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
                                    .then(
                                        when {
                                            record != null -> Modifier.clickable(
                                                onClickLabel = tr("Open entry"), role = Role.Button
                                            ) { onPick(record) }
                                            addable -> Modifier.clickable(
                                                onClickLabel = tr("Add a missed day"), role = Role.Button
                                            ) { onBackfill(date) }
                                            else -> Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$dayNumber",
                                    color = when {
                                        record != null -> Background
                                        date.isAfter(today) -> TextSecondary.copy(alpha = 0.4f)
                                        else -> TextSecondary
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (record != null) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            trf("Tap a day with a faint ring to add a missed check-in (up to %1\$d days back).", BACKFILL_DAYS),
            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            moodOptions.reversed().forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(moodColorFor(it.score)))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(it.label), color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun BackfillDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (mood: Int, stress: Int, energy: Int, note: String, tags: List<String>) -> Unit
) {
    var mood by remember(date) { mutableIntStateOf(3) }
    var stress by remember(date) { mutableIntStateOf(3) }
    var energy by remember(date) { mutableIntStateOf(3) }
    var note by remember(date) { mutableStateOf("") }
    var tags by remember(date) { mutableStateOf<List<String>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Column {
                Text(tr("Add a missed day"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(prettyDate(date.toString()), color = TextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("How were you feeling that day?"), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    moodOptions.reversed().forEach { option ->
                        val selected = mood == option.score
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) SurfaceSelected else androidx.compose.ui.graphics.Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { mood = option.score })
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MoodFace(score = option.score, selected = selected, modifier = Modifier.size(34.dp))
                            Text(tr(option.label), color = if (selected) Lavender else TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(trf("%1\$s: %2\$d / 5", tr("Stress level"), stress), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                BlockMeter(tr("Stress level"), stress) { stress = it }
                Spacer(Modifier.height(10.dp))
                Text(trf("%1\$s: %2\$d / 5", tr("Energy level"), energy), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                BlockMeter(tr("Energy level"), energy) { energy = it }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
        colors = quietFieldColors(),
                    value = note,
                    onValueChange = { if (it.length <= 2000) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("Add a note about this day"), color = TextSecondary) },
                    minLines = 2, maxLines = 5
                )
                Spacer(Modifier.height(10.dp))
                TagChips(
                    selected = tags,
                    onToggle = { tag ->
                        tags = when {
                            tag in tags -> tags - tag
                            tags.size >= MAX_TAGS -> tags
                            else -> tags + tag
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(mood, stress, energy, note, tags) },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = TextSecondary) } }
    )
}

@Composable
private fun EditEntryDialog(
    record: CheckInRecord,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember(record.date) { mutableStateOf(record.journal) }
    var tags by remember(record.date) { mutableStateOf(record.tags) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceColor,
            title = { Text(tr("Delete this entry?"), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    trf("The check-in for %1\$s will be removed from this device and your account. This can't be undone.", prettyDate(record.date)),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Background)
                ) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Keep it"), color = TextSecondary) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text(prettyDate(record.date), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
        colors = quietFieldColors(),
                    value = text,
                    onValueChange = { if (it.length <= 2000) text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = { Text(tr("Add a note about this day"), color = TextSecondary) },
                    minLines = 3,
                    maxLines = 8
                )
                Spacer(Modifier.height(12.dp))
                Text(tr("Tags"), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TagChips(
                    selected = tags,
                    onToggle = { tag ->
                        tags = when {
                            tag in tags -> tags - tag
                            tags.size >= MAX_TAGS -> tags
                            else -> tags + tag
                        }
                    }
                )
                TextButton(onClick = { confirmDelete = true }) { Text(tr("Delete this entry"), color = Coral) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text.trim(), tags) },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = TextSecondary) } }
    )
}

@Composable
private fun ExportDialog(records: List<CheckInRecord>, name: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var range by remember { mutableStateOf(30) }          // 30, 90, or 0 = all time
    var includeNotes by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun inRange(): List<CheckInRecord> =
        if (range == 0) records else {
            val cutoff = LocalDate.now().minusDays(range - 1L)
            records.filter { !LocalDate.parse(it.date).isBefore(cutoff) }
        }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SurfaceColor,
        title = { Text(tr("Export PDF report"), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    tr("A readable summary with charts and explanations that you can share with a doctor. It is a self-reported summary, not a clinical assessment."),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(14.dp))
                listOf(30 to tr("Last 30 days"), 90 to tr("Last 90 days"), 0 to tr("All time")).forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(selected = range == value, role = Role.RadioButton, onClick = { range = value })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = range == value, onClick = null,
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Lavender)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .toggleable(value = includeNotes, role = Role.Checkbox, onValueChange = { includeNotes = it })
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = includeNotes, onCheckedChange = null,
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Lavender, checkmarkColor = Background)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Include my journal notes"), color = TextPrimary, fontSize = 14.sp)
                }
                Text(
                    tr("Notes are personal. Leave them out unless you want your doctor to read them."),
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
                )
                error?.let { Text(it, color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val selected = inRange()
                    if (selected.isEmpty()) {
                        error = tr("There are no check-ins in that period.")
                        return@Button
                    }
                    busy = true; error = null
                    scope.launch {
                        try {
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                ReportExporter.create(context, selected, includeNotes, name)
                            }
                            ReportExporter.share(context, file)
                            onDismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("MindTrackReport", "PDF export failed", e)
                            error = tr("Couldn't create the report. Please try again.")
                            busy = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(if (busy) tr("Creating…") else tr("Create PDF")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel"), color = TextSecondary) } }
    )
}
