package com.mindtrack.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.DayOfWeek
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val CHANNEL_ID = "mindtrack_daily_checkin"
internal const val KEY_LAST_CHECKIN = "last_checkin_date"

internal object ReminderScheduler {
    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7001,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Sets (or clears) the next alarm from the saved settings. Safe to call whenever anything changes. */
    fun rescheduleFromSettings(context: Context) {
        val prefs = settingsPrefs(context)
        val config = readReminderConfig(prefs)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!config.enabled) {
            alarms.cancel(pendingIntent(context))
            return
        }
        val next = nextTrigger(LocalDateTime.now(), effectiveTimes(config, readCheckInSamples(prefs)), config.days)
        if (next == null) {
            alarms.cancel(pendingIntent(context))
            return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact on purpose: no exact-alarm permission is needed and a few minutes of drift is fine.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleFromSettings(context)
            return
        }

        val prefs = settingsPrefs(context)
        Lang.current = readLanguage(prefs)
        // Always line up tomorrow's reminder first.
        ReminderScheduler.rescheduleFromSettings(context)

        if (prefs.getString(KEY_LAST_CHECKIN, null) == LocalDate.now().toString()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, tr("Daily check-in"), NotificationManager.IMPORTANCE_DEFAULT)
        )

        val open = PendingIntent.getActivity(
            context, 7002,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val config = readReminderConfig(prefs)
        val today = LocalDate.now()
        val kind = nudgeKind(daysSince(prefs.getString(KEY_LAST_CHECKIN, null), today), config.gentle, daysSince(prefs.getString("last_gentle_date", null), today))
        if (kind == NudgeKind.GENTLE_AFTER_ABSENCE) prefs.edit { putString("last_gentle_date", today.toString()) }
        val title = if (kind == NudgeKind.GENTLE_AFTER_ABSENCE) tr("No pressure, we're here when you are") else tr("How are you feeling today?")
        val text = if (kind == NudgeKind.GENTLE_AFTER_ABSENCE) tr("Whenever you feel like it, a short check-in is waiting. There's no streak to worry about.") else tr("Take a minute for your MindTrack check-in.")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(7003, notification)
    }

    companion object {
        const val ACTION_REMIND = "com.mindtrack.app.ACTION_REMIND"
    }
}

@Composable
internal fun ReminderCard() {
    val context = LocalContext.current
    val prefs = remember { settingsPrefs(context) }
    var config by remember { mutableStateOf(readReminderConfig(prefs)) }
    val samples = remember(config) { readCheckInSamples(prefs) }

    fun update(new: ReminderConfig) {
        config = new
        writeReminderConfig(prefs, new)
        ReminderScheduler.rescheduleFromSettings(context)
    }

    // The reminder is scheduled either way; without permission Android simply won't show it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        update(config.copy(enabled = true))
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, true).show()
    }

    Column(
        Modifier.fillMaxWidth().flatSection()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(tr("Daily reminder"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(tr("A gentle nudge to check in. Skipped if you've already checked in today."), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else update(config.copy(enabled = true))
                    } else update(config.copy(enabled = false))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Background)
            )
        }

        if (config.enabled) {
            Spacer(Modifier.height(14.dp))
            val smartMinute = if (config.smart) smartMinuteOfDay(samples) else null

            if (smartMinute != null) {
                Text(
                    trf("Using your usual check-in time: about %1\$s", String.format("%02d:%02d", smartMinute / 60, smartMinute % 60)),
                    color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(tr("Remind me at"), color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    config.times.forEach { time ->
                        TimeChip(
                            label = String.format("%02d:%02d", time.hour, time.minute),
                            onEdit = { pickTime(time) { new -> update(config.copy(times = (config.times - time + new).distinct().sorted())) } },
                            onRemove = if (config.times.size > 1) ({ update(config.copy(times = config.times - time)) }) else null
                        )
                    }
                    if (config.times.size < MAX_REMINDER_TIMES) {
                        Text(
                            tr("+ Add"),
                            color = Lavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button) {
                                    pickTime(LocalTime.of(9, 0)) { new -> update(config.copy(times = (config.times + new).distinct().sorted().take(MAX_REMINDER_TIMES))) }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(tr("On these days"), color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in config.days
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (on) Lavender.copy(alpha = 0.22f) else SurfaceElevated)
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = {
                                val days = if (on) config.days - day else config.days + day
                                if (days.isNotEmpty()) update(config.copy(days = days))
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.getDisplayName(java.time.format.TextStyle.NARROW, appLocale()),
                            color = if (on) Lavender else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OptionRow(
                title = tr("Smart timing"),
                description = tr("Learns when you usually check in and reminds you then. Needs about 5 check-ins."),
                checked = config.smart,
                onChange = { update(config.copy(smart = it)) }
            )
            OptionRow(
                title = tr("Gentle nudge after missed days"),
                description = tr("After a few days away, a softer message with no pressure."),
                checked = config.gentle,
                onChange = { update(config.copy(gentle = it)) }
            )
        }
    }
}

@Composable
private fun TimeChip(label: String, onEdit: () -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceElevated).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = tr("Change time"), onClick = onEdit).padding(vertical = 8.dp)
        )
        if (onRemove != null) {
            Text(
                "✕", color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = tr("Remove time"), onClick = onRemove).padding(horizontal = 10.dp, vertical = 8.dp)
            )
        } else Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun OptionRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Background)
        )
    }
}
