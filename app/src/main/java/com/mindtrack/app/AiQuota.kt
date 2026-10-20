package com.mindtrack.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The AI models the app may use, in order of preference.
 *
 * Google counts the free daily quota per model, so when the first model is used up the app can carry on
 * with the second. Both names are Gemini API model names; change them here if Google renames them.
 * Google does not publish the free-tier limit per model in its docs, so [ESTIMATED_DAILY_LIMIT] is an
 * estimate taken from the error the API returned for the first model (20 requests a day).
 */
internal object AiModels {
    const val PRIMARY = "gemini-3.5-flash"
    const val FALLBACK = "gemini-3.5-flash-lite"
    val all = listOf(PRIMARY, FALLBACK)
    const val ESTIMATED_DAILY_LIMIT = 20
}

/** Raised before any request is made when today's estimated allowance is already used up. */
internal class QuotaExhaustedException(val resetLabel: String) : Exception("Today's companion messages are used up.")

internal interface QuotaStore {
    fun day(): String?
    fun counts(): Map<String, Int>
    fun exhausted(): Set<String>
    fun save(day: String, counts: Map<String, Int>, exhausted: Set<String>)
}

internal class PrefsQuotaStore(private val prefs: SharedPreferences) : QuotaStore {
    override fun day() = prefs.getString("day", null)
    override fun counts() = AiModels.all.associateWith { prefs.getInt("count_$it", 0) }
    override fun exhausted() = prefs.getStringSet("exhausted", emptySet()).orEmpty().toSet()
    override fun save(day: String, counts: Map<String, Int>, exhausted: Set<String>) {
        prefs.edit {
            putString("day", day)
            counts.forEach { (model, n) -> putInt("count_$model", n) }
            putStringSet("exhausted", exhausted)
        }
    }
}

private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

/** Google's daily quotas reset at midnight Pacific time, so "today" is counted in that zone. */
internal fun quotaDay(now: ZonedDateTime): String = now.withZoneSameInstant(PACIFIC).toLocalDate().toString()

/** When the next reset happens, written in the person's own time zone, e.g. "12:30 PM". */
internal fun nextQuotaResetLabel(
    now: ZonedDateTime = ZonedDateTime.now(),
    locale: Locale = Locale.getDefault()
): String {
    val reset = now.withZoneSameInstant(PACIFIC).toLocalDate().plusDays(1).atStartOfDay(PACIFIC)
    return reset.withZoneSameInstant(now.zone).format(DateTimeFormatter.ofPattern("h:mm a", locale))
}

/**
 * A device-side estimate of today's free AI allowance. It cannot see other devices or Google's own count:
 * the real quota is shared by everyone using the project, and the server's answer always wins.
 */
internal class AiQuota(
    private val store: QuotaStore,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() }
) {
    private var cachedDay: String? = null
    private var counts: MutableMap<String, Int> = mutableMapOf()
    private var exhausted: MutableSet<String> = mutableSetOf()

    private fun load() {
        val today = quotaDay(clock())
        if (store.day() == today) {
            counts = store.counts().toMutableMap()
            exhausted = store.exhausted().toMutableSet()
        } else {
            counts = mutableMapOf()
            exhausted = mutableSetOf()
        }
        cachedDay = today
    }

    private fun save() = store.save(quotaDay(clock()), counts, exhausted)

    fun used(model: String): Int { load(); return counts[model] ?: 0 }

    fun isExhausted(model: String): Boolean {
        load()
        return model in exhausted || (counts[model] ?: 0) >= AiModels.ESTIMATED_DAILY_LIMIT
    }

    fun availableModels(): List<String> = AiModels.all.filter { !isExhausted(it) }

    /** A successful request: counts against that model. */
    fun record(model: String) {
        load()
        counts[model] = (counts[model] ?: 0) + 1
        save()
    }

    /** The server said this model has no quota left today. */
    fun markExhausted(model: String) {
        load()
        exhausted += model
        save()
    }

    /** Estimated messages left across all models that still have quota. */
    fun remaining(): Int {
        load()
        return AiModels.all.filter { it !in exhausted }
            .sumOf { (AiModels.ESTIMATED_DAILY_LIMIT - (counts[it] ?: 0)).coerceAtLeast(0) }
    }

    fun resetLabel(): String = nextQuotaResetLabel(clock())

    companion object {
        fun forContext(context: Context) = AiQuota(
            PrefsQuotaStore(context.applicationContext.getSharedPreferences("mindtrack_ai_quota", Context.MODE_PRIVATE))
        )
    }
}
