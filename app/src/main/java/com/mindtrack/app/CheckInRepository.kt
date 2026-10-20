package com.mindtrack.app

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Single place that reads and writes check-ins: the on-device copy is always written first and is the
 * source of truth for the UI; the cloud copy follows, and anything the cloud hasn't confirmed stays on a
 * pending list that is retried later.
 */
internal class CheckInRepository(
    private val prefs: SharedPreferences,
    private val cloud: CheckInCloud
) {
    fun all(): List<CheckInRecord> = loadAllRecords(prefs)

    fun get(date: String): CheckInRecord? = all().firstOrNull { it.date == date }

    fun hasCheckIn(date: String) = prefs.getBoolean("checkin_$date", false)

    fun draftFor(date: String) = Draft(
        mood = prefs.getInt("mood_$date", 3),
        stress = prefs.getInt("stress_$date", 3),
        energy = prefs.getInt("energy_$date", 3),
        journal = prefs.getString("journal_$date", "") ?: "",
        tags = readTags(prefs, date)
    )

    data class Draft(val mood: Int, val stress: Int, val energy: Int, val journal: String, val tags: List<String>)

    fun save(record: CheckInRecord) {
        prefs.edit {
            putInt("mood_${record.date}", record.mood)
            putInt("stress_${record.date}", record.stress)
            putInt("energy_${record.date}", record.energy)
            putString("journal_${record.date}", record.journal.trim().take(2000))
            putStringSet("tags_${record.date}", record.tags.filter { it in moodTags }.take(MAX_TAGS).toSet())
            putBoolean("checkin_${record.date}", true)
            // Preserve compatibility with the original prototype.
            putInt("mood", record.mood)
            putInt("stress", record.stress)
            putInt("energy", record.energy)
            putBoolean("checked_in", true)
            putString("checkin_date", record.date)
        }
        markPendingSync(prefs, record.date)
    }

    fun delete(date: String) {
        prefs.edit {
            remove("checkin_$date"); remove("mood_$date"); remove("stress_$date")
            remove("energy_$date"); remove("journal_$date"); remove("tags_$date")
        }
        markPendingSync(prefs, date)   // the cloud copy is removed on the next sync
    }

    fun dismissInsight(id: String) {
        prefs.edit { putStringSet("dismissed_insights", dismissedInsights(prefs) + id) }
    }

    fun clearLocal() = prefs.edit { clear() }

    /** Pushes one day (or its deletion) to the cloud. Returns true once the cloud has confirmed it. */
    suspend fun pushToCloud(date: String): Boolean = try {
        withTimeout(10_000) {
            val record = get(date)
            if (record == null) cloud.deleteCheckIn(date)
            else cloud.saveCheckIn(record.date, record.mood, record.stress, record.energy, record.journal, record.tags)
        }
        clearPendingSync(prefs, date)
        true
    } catch (e: CancellationException) {
        if (e is TimeoutCancellationException) false else throw e
    } catch (e: Exception) {
        false
    }

    suspend fun syncPending() {
        pendingSyncDates(prefs).forEach { pushToCloud(it) }
    }

    /** Brings in days that exist only in the cloud. Never overwrites, and skips days with unsynced local changes. */
    suspend fun importFromCloud(): Int {
        val pending = pendingSyncDates(prefs)
        var imported = 0
        cloud.loadCheckIns(limit = 400).forEach { record ->
            val date = record["date"] as? String ?: return@forEach
            if (date in pending || hasCheckIn(date)) return@forEach
            val mood = (record["mood"] as? Number)?.toInt() ?: return@forEach
            val stress = (record["stress"] as? Number)?.toInt() ?: return@forEach
            val energy = (record["energy"] as? Number)?.toInt() ?: return@forEach
            val tags = (record["tags"] as? List<*>).orEmpty().filterIsInstance<String>()
            prefs.edit {
                putInt("mood_$date", mood)
                putInt("stress_$date", stress)
                putInt("energy_$date", energy)
                putString("journal_$date", record["journal"] as? String ?: "")
                putStringSet("tags_$date", tags.filter { it in moodTags }.toSet())
                putBoolean("checkin_$date", true)
            }
            imported++
        }
        return imported
    }
}
