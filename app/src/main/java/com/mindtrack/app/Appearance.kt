package com.mindtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

/** Light, dark, or follow the phone. The choice is remembered on this device. */
@Composable
internal fun AppearanceCard() {
    val context = LocalContext.current
    val prefs = remember { settingsPrefs(context) }
    val options = listOf(
        ThemeMode.SYSTEM to tr("Auto"),
        ThemeMode.LIGHT to tr("Light"),
        ThemeMode.DARK to tr("Dark")
    )

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)) {
        Text(tr("Appearance"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(tr("Auto follows your phone's light or dark setting."), color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceElevated).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (mode, label) ->
                val selected = ThemeState.mode == mode
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) Lavender.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            ThemeState.mode = mode
                            prefs.edit { putString("theme_mode", mode.name) }
                        })
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Lavender else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(tr("Language"), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceElevated).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppLanguage.entries.forEach { language ->
                val selected = Lang.current == language
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) Lavender.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = {
                            if (!selected) {
                                saveLanguage(prefs, language)
                                // Restart so every screen, notification and AI prompt picks up the new language.
                                var ctx = context
                                while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
                                (ctx as? android.app.Activity)?.recreate()
                            }
                        })
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        language.nativeName,
                        color = if (selected) Lavender else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Lets the person turn crash reports off. In debug builds it also offers a test crash. */
@Composable
internal fun CrashReportsCard() {
    val context = LocalContext.current
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashReporting.isOn(context)) }
    val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(tr("Crash reports"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Send technical details when the app crashes, so bugs can be fixed. Never includes your notes or messages."),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = on,
                onCheckedChange = { on = it; CrashReporting.setOn(context, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Background)
            )
        }
        if (debuggable) {
            androidx.compose.material3.TextButton(onClick = {
                // Debug builds only: lets you confirm in the Firebase console that crash reports arrive.
                throw RuntimeException("MindTrack test crash")
            }) { Text(tr("Send a test crash (debug build)"), color = Coral, fontSize = 12.sp) }
        }
    }
}

/** Shows what the companion remembers (on this device only, for 7 days) and lets you wipe it. */
@Composable
internal fun CompanionMemoryCard(count: Int, since: Long?, onClear: () -> Unit) {
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).padding(20.dp)) {
        Text(tr("Companion memory"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("The companion remembers your chats from the last 7 days, so you don't have to repeat yourself. They stay on this device only and are deleted automatically after 7 days."),
            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        val label = if (count == 0 || since == null) tr("Nothing remembered right now.")
        else trf("%1\$d messages remembered, since %2\$s.", count,
            java.time.Instant.ofEpochMilli(since).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", appLocale())))
        Text(label, color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (count > 0) {
            androidx.compose.material3.TextButton(onClick = { confirm = true }) { Text(tr("Forget everything"), color = Coral) }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = SurfaceColor,
            title = { Text(tr("Forget everything?"), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(tr("This deletes your chat with the companion from this device. Your check-ins are not affected."), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { confirm = false; onClear() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Background)
                ) { Text(tr("Forget")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text(tr("Cancel"), color = TextSecondary) } }
        )
    }
}
