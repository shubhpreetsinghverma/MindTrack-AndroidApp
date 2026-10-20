package com.mindtrack.app

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pattern observations drawn only from what the person recorded. They are phrased as possibilities, need a
 * minimum number of days before they appear, and always carry a plain description of how they were worked out.
 */
internal data class PatternInsight(
    val id: String,
    val title: String,
    val detail: String,
    val how: String
)

private const val MIN_RECORDS = 14
private const val MIN_GROUP = 4

private fun f(v: Double) = String.format(java.util.Locale.ENGLISH, "%.1f", v)

internal fun findPatterns(records: List<CheckInRecord>): List<PatternInsight> {
    if (records.size < MIN_RECORDS) return emptyList()
    val out = mutableListOf<PatternInsight>()

    // 1. Days with a tag versus days without it.
    moodTags.forEach { tag ->
        val with = records.filter { tag in it.tags }
        val without = records.filter { tag !in it.tags }
        if (with.size < MIN_GROUP || without.size < MIN_GROUP) return@forEach

        val shownTag = tr(tag)
        val moodDiff = with.map { it.mood }.average() - without.map { it.mood }.average()
        if (abs(moodDiff) >= 0.6) {
            out += PatternInsight(
                id = "tag-mood-$tag",
                title = trf("Mood and “%1\$s”", shownTag),
                detail = trf(
                    "Your mood averaged %1\$s on the %2\$d days you tagged “%3\$s”, compared with %4\$s on your other %5\$d days. That may be a coincidence, but it could be worth noticing.",
                    f(with.map { it.mood }.average()), with.size, shownTag, f(without.map { it.mood }.average()), without.size
                ),
                how = tr("We split your check-ins into days with this tag and days without it and compared the average mood rating. This only shows that the two groups differ. It cannot show that one caused the other.")
            )
        }
        val stressDiff = with.map { it.stress }.average() - without.map { it.stress }.average()
        if (abs(stressDiff) >= 0.8) {
            out += PatternInsight(
                id = "tag-stress-$tag",
                title = trf("Stress and “%1\$s”", shownTag),
                detail = trf(
                    "Stress averaged %1\$s on days tagged “%2\$s”, versus %3\$s on other days.",
                    f(with.map { it.stress }.average()), shownTag, f(without.map { it.stress }.average())
                ),
                how = trf("Same comparison as above, using the stress rating. Based on %1\$d tagged and %2\$d untagged days.", with.size, without.size)
            )
        }
    }

    // 2. Weekday differences (needs enough weeks of data).
    if (records.size >= 21) {
        val byDay = records.groupBy { LocalDate.parse(it.date).dayOfWeek }.filterValues { it.size >= 3 }
        if (byDay.size >= 4) {
            val avg = byDay.mapValues { (_, l) -> l.map { it.mood }.average() }
            val best = avg.maxByOrNull { it.value }!!
            val worst = avg.minByOrNull { it.value }!!
            if (best.key != worst.key && best.value - worst.value >= 0.8) {
                out += PatternInsight(
                    id = "weekday-mood",
                    title = tr("Your week has a rhythm"),
                    detail = trf(
                        "Mood tended to be highest on %1\$s (%2\$s) and lowest on %3\$s (%4\$s).",
                        weekdayName(best.key), f(best.value), weekdayName(worst.key), f(worst.value)
                    ),
                    how = tr("We averaged mood for each weekday that has at least 3 check-ins and compared the highest and lowest. With only a few weeks of data, chance can easily produce a gap like this.")
                )
            }
        }
    }

    // 3. Stress and energy moving together or apart.
    if (records.size >= 20) {
        val r = correlation(records.map { it.stress.toDouble() }, records.map { it.energy.toDouble() })
        if (r != null && abs(r) >= 0.5) {
            out += PatternInsight(
                id = "stress-energy",
                title = if (r < 0) tr("Stress and energy often move in opposite directions") else tr("Stress and energy often rise together"),
                detail = if (r < 0) tr("On days you rated stress higher, you tended to rate energy lower.")
                else tr("On days you rated stress higher, you also tended to rate energy higher."),
                how = trf(
                    "We measured how closely your stress and energy ratings move together across all %1\$d check-ins (a correlation of %2\$s, where 0 means no relationship and ±1 a perfect one). It describes a tendency, not a cause.",
                    records.size, f(r)
                )
            )
        }
    }
    return out
}

internal fun correlation(a: List<Double>, b: List<Double>): Double? {
    if (a.size != b.size || a.size < 3) return null
    val ma = a.average()
    val mb = b.average()
    var num = 0.0; var da = 0.0; var db = 0.0
    for (i in a.indices) {
        num += (a[i] - ma) * (b[i] - mb)
        da += (a[i] - ma) * (a[i] - ma)
        db += (b[i] - mb) * (b[i] - mb)
    }
    if (da == 0.0 || db == 0.0) return null
    return num / sqrt(da * db)
}

/** A weekday name in the app's language, plural-agnostic ("Thursdays" in English, "गुरुवार" in Hindi). */
internal fun weekdayName(day: DayOfWeek): String =
    if (Lang.current == AppLanguage.ENGLISH) day.pretty() + "s" else day.getDisplayName(java.time.format.TextStyle.FULL, appLocale())
