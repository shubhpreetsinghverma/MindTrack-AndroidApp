package com.mindtrack.app

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetTest {
    @After fun resetLanguage() { Lang.current = AppLanguage.ENGLISH }

    private val wednesday = LocalDate.of(2026, 10, 21)

    private fun rec(date: LocalDate, mood: Int = 3, stress: Int = 3, energy: Int = 3, note: String = "") =
        CheckInRecord(date.toString(), mood, stress, energy, note)

    // ---- week ----

    @Test fun weekRunsMondayToSundayAndMarksTodayAndTheFuture() {
        val week = weekOf(emptyList(), wednesday)
        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.last().date.dayOfWeek)
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.isToday })
        assertEquals(listOf(false, false, false, true, true, true, true), week.map { it.isFuture })
    }

    @Test fun weekOnASundayStillStartsOnTheMondayBefore() {
        val sunday = LocalDate.of(2026, 10, 25)
        val week = weekOf(emptyList(), sunday)
        assertEquals(LocalDate.of(2026, 10, 19), week.first().date)
        assertTrue(week.last().isToday)
    }

    @Test fun weekProgressCountsOnlyDaysSoFar() {
        val records = listOf(rec(LocalDate.of(2026, 10, 19)), rec(wednesday), rec(LocalDate.of(2026, 10, 10)))
        assertEquals(2 to 3, weekProgress(weekOf(records, wednesday)))
    }

    // ---- small thing ----

    @Test fun lowMoodIsMetWithCareEvenWhenStressIsAlsoHigh() {
        assertEquals(SmallThingKind.LOW_MOOD, smallThingKindFor(rec(wednesday, mood = 1, stress = 5)))
        assertEquals(SmallThingKind.STRESS, smallThingKindFor(rec(wednesday, mood = 3, stress = 4)))
        assertEquals(SmallThingKind.LOW_ENERGY, smallThingKindFor(rec(wednesday, mood = 3, energy = 1)))
        assertEquals(SmallThingKind.GOOD_DAY, smallThingKindFor(rec(wednesday, mood = 5, stress = 1, energy = 5)))
        assertEquals(SmallThingKind.GENERAL, smallThingKindFor(null))
    }

    @Test fun noDailySuggestionTellsYouToBreatheBecauseThatHasItsOwnWidget() {
        allSmallThingTexts.forEach {
            assertFalse("Mentions breathing: $it", it.contains("breath", ignoreCase = true))
        }
    }

    @Test fun suggestionIsNeverTheSameTwoDaysRunning() {
        SmallThingKind.entries.forEach { kind ->
            val record = when (kind) {
                SmallThingKind.LOW_MOOD -> rec(wednesday, mood = 1)
                SmallThingKind.STRESS -> rec(wednesday, stress = 5)
                SmallThingKind.LOW_ENERGY -> rec(wednesday, energy = 1)
                SmallThingKind.GOOD_DAY -> rec(wednesday, mood = 5)
                SmallThingKind.GENERAL -> null
            }
            // Keep the same reference record "fresh" for each day we test.
            val texts = (0..30).map { d ->
                val day = wednesday.plusDays(d.toLong())
                val records = record?.let { listOf(it.copy(date = day.toString())) }.orEmpty()
                smallThingFor(records, day).text
            }
            texts.zipWithNext().forEach { (a, b) -> assertNotEquals("Repeated for $kind", a, b) }
        }
    }

    @Test fun suggestionIsStableThroughTheDay() {
        val records = listOf(rec(wednesday, mood = 2))
        assertEquals(smallThingFor(records, wednesday), smallThingFor(records, wednesday))
    }

    @Test fun anOldCheckInDoesNotSteerTodaysSuggestion() {
        val old = listOf(rec(wednesday.minusDays(10), mood = 1))
        assertEquals(SmallThingKind.GENERAL, smallThingFor(old, wednesday).kind)
    }

    @Test fun everySuggestionHasHindi() {
        Lang.current = AppLanguage.HINDI
        allSmallThingTexts.forEach { assertNotEquals("No Hindi for: $it", it, tr(it)) }
    }

    // ---- on this day ----

    @Test fun showsAWeekAgoBeforeAMonthAgo() {
        val records = listOf(rec(wednesday.minusDays(30), mood = 2), rec(wednesday.minusDays(7), mood = 5))
        val item = onThisDayFor(records, wednesday)
        assertEquals("A week ago", item?.label)
        assertEquals(5, item?.record?.mood)
    }

    @Test fun fallsBackToAMonthAgoAndHidesWhenThereIsNothing() {
        assertEquals("A month ago", onThisDayFor(listOf(rec(wednesday.minusDays(30), mood = 5)), wednesday)?.label)
        assertNull(onThisDayFor(listOf(rec(wednesday.minusDays(8))), wednesday))
        assertNull(onThisDayFor(emptyList(), wednesday))
    }

    @Test fun hardDaysAreNeverBroughtBackByTheLookBack() {
        assertNull(onThisDayFor(listOf(rec(wednesday.minusDays(7), mood = 2)), wednesday))
        assertNull(onThisDayFor(listOf(rec(wednesday.minusDays(30), mood = 1), rec(wednesday.minusDays(7), mood = 3)), wednesday))
        assertNotNull(onThisDayFor(listOf(rec(wednesday.minusDays(7), mood = 4)), wednesday))
    }

    @Test fun longNotesAreShortenedAndWhitespaceTidied() {
        assertEquals("a b c", shortNote("  a \n b   c "))
        val long = shortNote("word ".repeat(60))
        assertTrue(long.length <= 141)
        assertTrue(long.endsWith("…"))
        assertFalse(shortNote("short").endsWith("…"))
    }

    @Test fun lookBackLabelsAndSentenceAreTranslated() {
        Lang.current = AppLanguage.HINDI
        assertNotEquals("A week ago", tr("A week ago"))
        assertNotEquals("A month ago", tr("A month ago"))
        assertNotNull(trf("You felt %1\$s.", "अच्छा"))
        assertTrue(trf("%1\$d of %2\$d days", 3, 4).contains("3"))
    }
}

class TopPatternTest {
    private val start = java.time.LocalDate.of(2026, 9, 1)

    /** Twenty days: those tagged "Exercise" are good days, the rest are hard ones. */
    private fun records() = (0 until 20).map { i ->
        val tagged = i % 2 == 0
        CheckInRecord(
            date = start.plusDays(i.toLong()).toString(),
            mood = if (tagged) 5 else 2,
            stress = if (tagged) 2 else 4,
            energy = if (tagged) 4 else 2,
            journal = "",
            tags = if (tagged) listOf("Exercise") else emptyList()
        )
    }

    @Test fun nothingAppearsWhileThereIsTooLittleData() {
        org.junit.Assert.assertNull(topPattern(records().take(10), emptySet()))
        org.junit.Assert.assertNull(topPattern(emptyList(), emptySet()))
    }

    @Test fun aClearPatternIsPickedWhenThereIsEnoughData() {
        org.junit.Assert.assertNotNull(topPattern(records(), emptySet()))
    }

    @Test fun dismissedPatternsDoNotComeBack() {
        val all = findPatterns(records())
        org.junit.Assert.assertTrue(all.isNotEmpty())
        org.junit.Assert.assertNull(topPattern(records(), all.map { it.id }.toSet()))
        if (all.size > 1) org.junit.Assert.assertEquals(all[1].id, topPattern(records(), setOf(all[0].id))?.id)
    }
}
