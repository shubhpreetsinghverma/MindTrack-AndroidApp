package com.mindtrack.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Pure logic behind the Home widgets, kept apart from the screen so it can be unit tested.

/** One day of the current week (Monday to Sunday). */
internal data class WeekDay(val date: LocalDate, val record: CheckInRecord?, val isToday: Boolean, val isFuture: Boolean)

internal fun weekOf(records: List<CheckInRecord>, today: LocalDate): List<WeekDay> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val byDate = records.associateBy { it.date }
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        WeekDay(date, byDate[date.toString()], isToday = date == today, isFuture = date.isAfter(today))
    }
}

/** How many of the days so far this week have a check-in, and how many days have passed. */
internal fun weekProgress(week: List<WeekDay>): Pair<Int, Int> =
    week.count { it.record != null } to week.count { !it.isFuture }

internal enum class SmallThingKind { LOW_MOOD, STRESS, LOW_ENERGY, GOOD_DAY, GENERAL }

internal data class SmallThing(val kind: SmallThingKind, val text: String)

// Breathing lives in its own Home widget, so none of these suggest it.
private val smallThingPools: Map<SmallThingKind, List<String>> = mapOf(
    SmallThingKind.LOW_MOOD to listOf(
        "Be gentle with yourself today. A warm drink or a shower counts as looking after yourself.",
        "You don't have to do much. Pick one small thing, like opening a window or having a glass of water.",
        "If you can, tell someone you trust how today is going. A short message is enough.",
        "Rest isn't giving up. Give yourself permission to go slowly today."
    ),
    SmallThingKind.STRESS to listOf(
        "Let your shoulders drop and unclench your jaw, and loosen your hands.",
        "Stand up and stretch your arms overhead, then let them drop. Do it twice.",
        "Write down the one thing worrying you most. Getting it out of your head can make it feel smaller.",
        "Step away from your screen for five minutes, even if it's just to look out of a window."
    ),
    SmallThingKind.LOW_ENERGY to listOf(
        "Energy is low. A glass of water and a few minutes of daylight can help a little.",
        "Try a short stretch or a five-minute walk, only if it feels okay.",
        "Check the basics: have you eaten and had some water today?",
        "An early night can do more than another coffee. Plan a gentle wind-down tonight."
    ),
    SmallThingKind.GOOD_DAY to listOf(
        "Notice one thing that went well today and say thank you for it, even if only in your head.",
        "Good days are worth saving. Add a note to today's check-in about what helped.",
        "Share the good mood: send a kind message to someone you haven't spoken to in a while.",
        "Do something just because you enjoy it, for ten minutes."
    ),
    SmallThingKind.GENERAL to listOf(
        "Name three things you can see around you right now.",
        "Drink a glass of water and look away from your screen for a minute.",
        "Name how you feel in one word. Putting a name on it helps.",
        "Step outside for a few minutes of fresh air if you can.",
        "Ask yourself: what do I need most right now?"
    )
)

internal val allSmallThingTexts: List<String> get() = smallThingPools.values.flatten()

/** The check-in the suggestion is based on: today's, or the latest from the last two days. */
private fun referenceRecord(records: List<CheckInRecord>, today: LocalDate): CheckInRecord? =
    records.filter { it.date <= today.toString() && it.date >= today.minusDays(2).toString() }.maxByOrNull { it.date }

internal fun smallThingKindFor(record: CheckInRecord?): SmallThingKind = when {
    record == null -> SmallThingKind.GENERAL
    record.mood <= 2 -> SmallThingKind.LOW_MOOD      // care comes before tasks
    record.stress >= 4 -> SmallThingKind.STRESS
    record.energy <= 2 -> SmallThingKind.LOW_ENERGY
    record.mood >= 4 -> SmallThingKind.GOOD_DAY
    else -> SmallThingKind.GENERAL
}

/**
 * One suggestion for today. It rotates through its group one step per day, so it is never the same two
 * days running, and it is the same all day (it does not change each time Home is opened).
 */
internal fun smallThingFor(records: List<CheckInRecord>, today: LocalDate): SmallThing {
    val kind = smallThingKindFor(referenceRecord(records, today))
    val pool = smallThingPools.getValue(kind)
    return SmallThing(kind, tr(pool[(today.toEpochDay() % pool.size).toInt()]))
}

/**
 * A look back at a week ago, or failing that a month ago, but only when that day was a good one, and never with
 * what was written. Nobody should be handed a hard day again by surprise.
 */
internal data class OnThisDay(val label: String, val record: CheckInRecord)

internal fun onThisDayFor(records: List<CheckInRecord>, today: LocalDate): OnThisDay? {
    val byDate = records.associateBy { it.date }
    return listOf(7L to "A week ago", 30L to "A month ago").firstNotNullOfOrNull { (days, label) ->
        byDate[today.minusDays(days).toString()]?.takeIf { it.mood >= 4 }?.let { OnThisDay(label, it) }
    }
}

internal fun shortNote(note: String, limit: Int = 140): String {
    val clean = note.trim().replace(Regex("\\s+"), " ")
    return if (clean.length <= limit) clean else clean.take(limit).trimEnd() + "…"
}

/** The first pattern worth showing on Home: one the person has not dismissed, or nothing while there is too little data. */
internal fun topPattern(records: List<CheckInRecord>, dismissed: Set<String>): PatternInsight? =
    findPatterns(records).firstOrNull { it.id !in dismissed }
