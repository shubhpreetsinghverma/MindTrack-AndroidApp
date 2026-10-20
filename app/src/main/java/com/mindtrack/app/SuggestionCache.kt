package com.mindtrack.app

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * One AI request per day per set of check-ins. The suggestions for today's check-ins are kept on the
 * device and reused every time the panel is opened. Journal text is never part of the signature.
 */
internal object SuggestionCache {

    fun signature(records: List<CheckInRecord>): String =
        records.sortedBy { it.date }.joinToString("|") {
            "${it.date}:${it.mood}${it.stress}${it.energy}:${it.tags.sorted().joinToString(",")}"
        }

    /** True when text saved for ([savedDay], [savedSignature]) is still the right answer for today. */
    fun isFresh(savedDay: String?, savedSignature: String?, today: String, signature: String) =
        savedDay == today && savedSignature == signature

    fun get(prefs: SharedPreferences, today: String, signature: String): String? {
        if (!isFresh(prefs.getString("sugg_day", null), prefs.getString("sugg_sig", null), today, signature)) return null
        return prefs.getString("sugg_text", null)?.takeIf { it.isNotBlank() }
    }

    fun put(prefs: SharedPreferences, today: String, signature: String, text: String) {
        prefs.edit {
            putString("sugg_day", today)
            putString("sugg_sig", signature)
            putString("sugg_text", text)
        }
    }
}
