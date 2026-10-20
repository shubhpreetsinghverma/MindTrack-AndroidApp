package com.mindtrack.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate
import java.util.Locale

private const val LEGACY_PREFS = "mindtrack_local"
internal const val SETTINGS_PREFS = "mindtrack_settings"

/** Device-wide settings that are not tied to one account (reminders, intro, widget). */
internal fun settingsPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

/**
 * Check-ins are stored per account so two people signing in on one device never
 * see each other's entries. Data saved by the original single-user prototype is
 * claimed once by the first account that opens the app.
 */
internal fun userPrefs(context: Context, uid: String?): SharedPreferences {
    val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    if (uid.isNullOrBlank()) return legacy

    val prefs = context.getSharedPreferences("${LEGACY_PREFS}_$uid", Context.MODE_PRIVATE)
    if (!legacy.getBoolean("migrated", false)) {
        val old = legacy.all
        if (old.isNotEmpty() && prefs.all.isEmpty()) {
            prefs.edit {
                old.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }
        }
        legacy.edit {
            clear()
            putBoolean("migrated", true)
        }
    }
    return prefs
}

internal fun readTags(prefs: SharedPreferences, date: String): List<String> =
    prefs.getStringSet("tags_$date", emptySet()).orEmpty()
        .filter { it in moodTags }
        .sortedBy { moodTags.indexOf(it) }

internal fun dismissedInsights(prefs: SharedPreferences): Set<String> =
    prefs.getStringSet("dismissed_insights", emptySet()).orEmpty().toSet()

private val DATE_KEY = Regex("""^checkin_(\d{4}-\d{2}-\d{2})$""")

/** Every saved check-in, newest first. */
internal fun loadAllRecords(prefs: SharedPreferences): List<CheckInRecord> =
    prefs.all.keys
        .mapNotNull { key -> DATE_KEY.matchEntire(key)?.groupValues?.get(1) }
        .filter { prefs.getBoolean("checkin_$it", false) }
        .sortedDescending()
        .map { date ->
            CheckInRecord(
                date = date,
                mood = prefs.getInt("mood_$date", 3),
                stress = prefs.getInt("stress_$date", 3),
                energy = prefs.getInt("energy_$date", 3),
                journal = prefs.getString("journal_$date", "") ?: "",
                tags = readTags(prefs, date)
            )
        }

/**
 * Consecutive days with a check-in. A streak stays alive until the end of the
 * day after the last check-in, so it never shows as broken before the user has
 * had a chance to check in today.
 */
internal fun streakFrom(checkedDates: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in checkedDates) today else today.minusDays(1)
    var streak = 0
    while (day in checkedDates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

internal fun computeStreak(prefs: SharedPreferences, today: LocalDate): Int =
    streakFrom(loadAllRecords(prefs).map { LocalDate.parse(it.date) }.toSet(), today)

internal data class WeeklySummary(
    val checkIns: Int,
    val averageMood: Double?,
    val averageStress: Double?,
    val averageEnergy: Double?,
    val moodChange: Double?
)

/** Last 7 days (including today) compared with the 7 days before. */
internal fun weeklySummaryOf(all: List<CheckInRecord>, today: LocalDate): WeeklySummary {
    fun between(from: LocalDate, to: LocalDate) = all.filter {
        val d = LocalDate.parse(it.date)
        !d.isBefore(from) && !d.isAfter(to)
    }

    val thisWeek = between(today.minusDays(6), today)
    val lastWeek = between(today.minusDays(13), today.minusDays(7))
    fun avg(list: List<CheckInRecord>, f: (CheckInRecord) -> Int) =
        if (list.isEmpty()) null else list.map(f).average()

    val moodNow = avg(thisWeek) { it.mood }
    val moodBefore = avg(lastWeek) { it.mood }
    return WeeklySummary(
        checkIns = thisWeek.size,
        averageMood = moodNow,
        averageStress = avg(thisWeek) { it.stress },
        averageEnergy = avg(thisWeek) { it.energy },
        moodChange = if (moodNow != null && moodBefore != null) moodNow - moodBefore else null
    )
}

internal fun computeWeeklySummary(prefs: SharedPreferences, today: LocalDate): WeeklySummary =
    weeklySummaryOf(loadAllRecords(prefs), today)

// ---- Offline sync queue -------------------------------------------------------------------

private const val KEY_PENDING = "pending_sync_dates"

/** Dates whose latest version has not yet been confirmed by the cloud. */
internal fun pendingSyncDates(prefs: SharedPreferences): Set<String> =
    prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

internal fun markPendingSync(prefs: SharedPreferences, date: String) {
    prefs.edit { putStringSet(KEY_PENDING, pendingSyncDates(prefs) + date) }
}

internal fun clearPendingSync(prefs: SharedPreferences, date: String) {
    prefs.edit { putStringSet(KEY_PENDING, pendingSyncDates(prefs) - date) }
}

internal fun formatScore(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.1f", value)

/** A short, private summary of recent check-ins that the companion can draw on. */
internal fun buildCompanionContext(records: List<CheckInRecord>, streak: Int): String {
    if (records.isEmpty()) return ""
    val lines = records.sortedBy { it.date }.takeLast(7).joinToString("\n") {
        val mood = moodOptions.firstOrNull { o -> o.score == it.mood }?.label ?: "Unknown"
        "${it.date}: mood $mood (${it.mood}/5), stress ${it.stress}/5, energy ${it.energy}/5"
    }
    return "Current check-in streak: $streak day(s).\nRecent check-ins:\n$lines"
}
