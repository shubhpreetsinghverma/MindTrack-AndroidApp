package com.mindtrack.app

import java.time.LocalDate

/** How many days back a missed check-in may be added. */
internal const val BACKFILL_DAYS = 30

/**
 * A missed day can be added if it is in the past (today uses the Mood tab), no more than
 * [BACKFILL_DAYS] days ago, and has no entry yet. Added days count toward streaks like any other.
 */
internal fun canBackfill(date: LocalDate, today: LocalDate, existingDates: Set<String>): Boolean =
    date.isBefore(today) &&
        !date.isBefore(today.minusDays(BACKFILL_DAYS.toLong())) &&
        date.toString() !in existingDates
