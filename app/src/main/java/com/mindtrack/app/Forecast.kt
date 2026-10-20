package com.mindtrack.app

import kotlin.math.abs

/**
 * An experimental, deliberately cautious forecast of the next mood rating.
 *
 * It is only shown after it has been checked against the person's own history: each candidate method is
 * scored by predicting every past entry from the entries before it (so it never peeks at the future) and
 * compared with the simplest possible baseline, "the average so far". If no method clearly beats the
 * baseline, no forecast is shown.
 */
internal sealed interface ForecastOutcome {
    data class TooEarly(val have: Int, val need: Int) : ForecastOutcome
    data class NotReliable(val baselineError: Double, val bestError: Double) : ForecastOutcome
    data class Ready(
        val predicted: Double,
        val method: String,
        val typicalError: Double,
        val baselineError: Double,
        val testedOn: Int
    ) : ForecastOutcome
}

internal const val FORECAST_MIN_ENTRIES = 21
private const val FORECAST_WARMUP = 7
private const val REQUIRED_IMPROVEMENT = 0.05   // must beat the baseline by at least 5%

private class Method(val name: String, val predict: (List<Int>) -> Double)

private val methods = listOf(
    Method("your last rating") { it.last().toDouble() },
    Method("the average of your last 3 ratings") { it.takeLast(3).average() },
    Method("the average of your last 7 ratings") { it.takeLast(7).average() }
)

private val baseline = Method("your average so far") { it.average() }

private fun meanAbsoluteError(method: Method, moods: List<Int>): Double {
    var total = 0.0
    var count = 0
    for (i in FORECAST_WARMUP until moods.size) {
        total += abs(method.predict(moods.subList(0, i)) - moods[i])
        count++
    }
    return total / count
}

/** [moodsOldestFirst] are the mood ratings in date order. */
internal fun evaluateForecast(moodsOldestFirst: List<Int>): ForecastOutcome {
    val n = moodsOldestFirst.size
    if (n < FORECAST_MIN_ENTRIES) return ForecastOutcome.TooEarly(n, FORECAST_MIN_ENTRIES)

    val baselineError = meanAbsoluteError(baseline, moodsOldestFirst)
    val best = methods.map { it to meanAbsoluteError(it, moodsOldestFirst) }.minByOrNull { it.second }!!

    return if (best.second <= baselineError * (1 - REQUIRED_IMPROVEMENT)) {
        ForecastOutcome.Ready(
            predicted = best.first.predict(moodsOldestFirst).coerceIn(1.0, 5.0),
            method = best.first.name,
            typicalError = best.second,
            baselineError = baselineError,
            testedOn = n - FORECAST_WARMUP
        )
    } else {
        ForecastOutcome.NotReliable(baselineError, best.second)
    }
}
