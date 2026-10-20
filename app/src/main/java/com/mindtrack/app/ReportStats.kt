package com.mindtrack.app

import java.time.DayOfWeek
import java.time.LocalDate

/** Plain numbers behind the PDF report. Kept free of Android types so it can be unit tested. */
internal data class ReportStats(
    val records: List<CheckInRecord>,           // oldest first
    val from: LocalDate,
    val to: LocalDate,
    val daysCovered: Int,
    val averageMood: Double,
    val averageStress: Double,
    val averageEnergy: Double,
    val longestStreak: Int,
    val moodCounts: Map<Int, Int>,              // score -> count, always has keys 1..5
    val weekdayMood: Map<DayOfWeek, Double>,    // only weekdays that have data
    val moodChange: Double?,                    // second half average minus first half average
    val highStressDays: Int,                    // stress >= 4
    val lowMoodDays: Int,                       // mood <= 2
    val lowEnergyDays: Int                      // energy <= 2
) {
    val consistencyPercent: Int get() = ((records.size * 100.0) / daysCovered).toInt().coerceIn(0, 100)
}

internal fun buildReportStats(input: List<CheckInRecord>): ReportStats? {
    if (input.isEmpty()) return null
    val records = input.sortedBy { it.date }
    val dates = records.map { LocalDate.parse(it.date) }
    val from = dates.first()
    val to = dates.last()

    val dateSet = dates.toSet()
    var longest = 0
    var run = 0
    var day = from
    while (!day.isAfter(to)) {
        if (day in dateSet) { run++; if (run > longest) longest = run } else run = 0
        day = day.plusDays(1)
    }

    val half = records.size / 2
    val change = if (records.size >= 4) {
        records.drop(half).map { it.mood }.average() - records.take(half).map { it.mood }.average()
    } else null

    return ReportStats(
        records = records,
        from = from,
        to = to,
        daysCovered = (to.toEpochDay() - from.toEpochDay() + 1).toInt(),
        averageMood = records.map { it.mood }.average(),
        averageStress = records.map { it.stress }.average(),
        averageEnergy = records.map { it.energy }.average(),
        longestStreak = longest,
        moodCounts = (1..5).associateWith { score -> records.count { it.mood == score } },
        weekdayMood = records
            .groupBy { LocalDate.parse(it.date).dayOfWeek }
            .mapValues { (_, list) -> list.map { it.mood }.average() },
        moodChange = change,
        highStressDays = records.count { it.stress >= 4 },
        lowMoodDays = records.count { it.mood <= 2 },
        lowEnergyDays = records.count { it.energy <= 2 }
    )
}

/**
 * Plain-language observations. They describe the numbers only: no diagnosis, no causes.
 */
internal fun describeObservations(stats: ReportStats): List<String> {
    val n = stats.records.size
    val lines = mutableListOf<String>()

    when (val change = stats.moodChange) {
        null -> lines += "There are only $n check-ins, which is too few to compare earlier and later periods. A trend becomes visible after about two weeks of regular entries."
        else -> lines += when {
            change >= 0.5 -> "Mood ratings in the second half of this period were higher on average than in the first half (by ${"%.1f".format(change)} points)."
            change <= -0.5 -> "Mood ratings in the second half of this period were lower on average than in the first half (by ${"%.1f".format(-change)} points)."
            else -> "Mood ratings stayed broadly steady between the first and second half of this period."
        }
    }

    lines += "Stress was rated 4 or 5 on ${stats.highStressDays} of $n recorded day${if (n == 1) "" else "s"} (${percent(stats.highStressDays, n)}%)."
    lines += "Mood was rated 1 or 2 (Rough or Low) on ${stats.lowMoodDays} of $n recorded day${if (n == 1) "" else "s"} (${percent(stats.lowMoodDays, n)}%)."
    lines += "Energy was rated 1 or 2 on ${stats.lowEnergyDays} of $n recorded day${if (n == 1) "" else "s"} (${percent(stats.lowEnergyDays, n)}%)."

    if (stats.weekdayMood.size >= 4 && n >= 14) {
        val best = stats.weekdayMood.maxByOrNull { it.value }!!
        val worst = stats.weekdayMood.minByOrNull { it.value }!!
        if (best.key != worst.key && best.value - worst.value >= 0.5) {
            lines += "Average mood was highest on ${best.key.pretty()}s (${"%.1f".format(best.value)}) and lowest on ${worst.key.pretty()}s (${"%.1f".format(worst.value)})."
        }
    }

    lines += "Check-ins were recorded on ${stats.consistencyPercent}% of the days in this period; the longest unbroken run was ${stats.longestStreak} day${if (stats.longestStreak == 1) "" else "s"}."
    return lines
}

private fun percent(part: Int, whole: Int) = if (whole == 0) 0 else (part * 100.0 / whole).toInt()

internal fun DayOfWeek.pretty(): String = name.lowercase().replaceFirstChar { it.uppercase() }
