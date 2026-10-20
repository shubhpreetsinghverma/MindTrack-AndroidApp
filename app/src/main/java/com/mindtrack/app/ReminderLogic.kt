package com.mindtrack.app

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Reminder settings. Times and days are kept as short text in the device settings. */
internal data class ReminderConfig(
    val enabled: Boolean,
    val times: List<LocalTime>,
    val days: Set<DayOfWeek>,
    val smart: Boolean,
    val gentle: Boolean
)

internal const val MAX_REMINDER_TIMES = 3
private const val MIN_SMART_SAMPLES = 5
private const val MAX_SAMPLES = 14

internal fun parseTimes(csv: String?): List<LocalTime> =
    csv.orEmpty().split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
        .distinct().sorted().take(MAX_REMINDER_TIMES)

internal fun formatTimes(times: List<LocalTime>): String = times.sorted().joinToString(",") { it.toString() }

internal fun parseDays(csv: String?): Set<DayOfWeek> {
    val parsed = csv.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSet()
    return parsed.ifEmpty { DayOfWeek.entries.toSet() }
}

internal fun formatDays(days: Set<DayOfWeek>): String = days.sorted().joinToString(",") { it.value.toString() }

/** The time of day (in minutes) you usually check in, once there are enough check-ins to say. */
internal fun smartMinuteOfDay(samples: List<Int>): Int? {
    if (samples.size < MIN_SMART_SAMPLES) return null
    val sorted = samples.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    // Never suggest a very early or very late nudge.
    return median.coerceIn(7 * 60, 22 * 60 + 30)
}

/** The times to use right now: the learned time when smart timing is on and ready, otherwise the chosen times. */
internal fun effectiveTimes(config: ReminderConfig, samples: List<Int>): List<LocalTime> {
    if (config.smart) smartMinuteOfDay(samples)?.let { return listOf(LocalTime.of(it / 60, it % 60)) }
    return config.times
}

/** The next moment a reminder is due after [now], or null if no day or time is set. */
internal fun nextTrigger(now: LocalDateTime, times: List<LocalTime>, days: Set<DayOfWeek>): LocalDateTime? {
    if (times.isEmpty() || days.isEmpty()) return null
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        if (date.dayOfWeek !in days) continue
        times.sorted().forEach { time ->
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(now)) return candidate
        }
    }
    return null
}

internal enum class NudgeKind { NORMAL, GENTLE_AFTER_ABSENCE }

/** A softer message after a few missed days, never more often than every 3 days. */
internal fun nudgeKind(daysSinceLastCheckIn: Long?, gentle: Boolean, daysSinceLastGentle: Long?): NudgeKind =
    if (gentle && daysSinceLastCheckIn != null && daysSinceLastCheckIn >= 3 &&
        (daysSinceLastGentle == null || daysSinceLastGentle >= 3)
    ) NudgeKind.GENTLE_AFTER_ABSENCE else NudgeKind.NORMAL

// ---- Storage -------------------------------------------------------------------------------

internal fun readReminderConfig(prefs: SharedPreferences): ReminderConfig {
    val legacy = if (prefs.contains("reminder_hour"))
        listOf(LocalTime.of(prefs.getInt("reminder_hour", 20).coerceIn(0, 23), prefs.getInt("reminder_minute", 0).coerceIn(0, 59)))
    else emptyList()
    val times = parseTimes(prefs.getString("reminder_times", null)).ifEmpty { legacy }.ifEmpty { listOf(LocalTime.of(20, 0)) }
    return ReminderConfig(
        enabled = prefs.getBoolean("reminder_enabled", false),
        times = times,
        days = parseDays(prefs.getString("reminder_days", null)),
        smart = prefs.getBoolean("reminder_smart", false),
        gentle = prefs.getBoolean("reminder_gentle", true)
    )
}

internal fun writeReminderConfig(prefs: SharedPreferences, config: ReminderConfig) {
    prefs.edit {
        putBoolean("reminder_enabled", config.enabled)
        putString("reminder_times", formatTimes(config.times))
        putString("reminder_days", formatDays(config.days))
        putBoolean("reminder_smart", config.smart)
        putBoolean("reminder_gentle", config.gentle)
    }
}

internal fun readCheckInSamples(prefs: SharedPreferences): List<Int> =
    prefs.getString("checkin_minutes", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }

/** Remembers when in the day a check-in was made, for smart timing. Keeps the most recent 14. */
internal fun recordCheckInTime(prefs: SharedPreferences, time: LocalTime) {
    val updated = (readCheckInSamples(prefs) + (time.hour * 60 + time.minute)).takeLast(MAX_SAMPLES)
    prefs.edit { putString("checkin_minutes", updated.joinToString(",")) }
}

internal fun daysSince(dateText: String?, today: LocalDate): Long? =
    runCatching { ChronoUnit.DAYS.between(LocalDate.parse(dateText), today) }.getOrNull()
