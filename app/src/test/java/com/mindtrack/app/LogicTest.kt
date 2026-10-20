package com.mindtrack.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

private fun rec(date: String, mood: Int = 3, stress: Int = 3, energy: Int = 3, journal: String = "") =
    CheckInRecord(date, mood, stress, energy, journal)

class StreakTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun noCheckInsMeansZero() = assertEquals(0, streakFrom(emptySet(), today))

    @Test fun countsConsecutiveDaysEndingToday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, streakFrom(days, today))
    }

    @Test fun streakSurvivesUntilEndOfNextDay() {
        // Checked in yesterday and the day before, but not yet today: still alive.
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, streakFrom(days, today))
    }

    @Test fun gapBreaksTheStreak() {
        val days = setOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, streakFrom(days, today))
    }

    @Test fun missingTodayAndYesterdayIsZero() {
        assertEquals(0, streakFrom(setOf(today.minusDays(2)), today))
    }
}

class WeeklySummaryTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun emptyWeekHasNoAverages() {
        val w = weeklySummaryOf(emptyList(), today)
        assertEquals(0, w.checkIns)
        assertNull(w.averageMood)
        assertNull(w.moodChange)
    }

    @Test fun averagesOnlyTheLastSevenDays() {
        val records = listOf(
            rec("2026-10-20", mood = 5, stress = 1, energy = 5),
            rec("2026-10-18", mood = 3, stress = 3, energy = 3),
            rec("2026-10-14", mood = 1),       // exactly 6 days ago: inside
            rec("2026-10-13", mood = 1)        // 7 days ago: outside, belongs to the previous week
        )
        val w = weeklySummaryOf(records, today)
        assertEquals(3, w.checkIns)
        assertEquals(3.0, w.averageMood!!, 0.001)
    }

    @Test fun comparesWithThePreviousWeek() {
        val records = listOf(
            rec("2026-10-20", mood = 5),
            rec("2026-10-12", mood = 3)
        )
        assertEquals(2.0, weeklySummaryOf(records, today).moodChange!!, 0.001)
    }

    @Test fun noChangeWithoutPreviousWeek() {
        assertNull(weeklySummaryOf(listOf(rec("2026-10-20", mood = 4)), today).moodChange)
    }
}

class CrisisSupportTest {
    @Test fun detectsClearPhrases() {
        listOf(
            "I want to die", "i've been thinking about suicide", "I might kill myself",
            "I wish I was dead", "thinking of self-harm", "I don’t want to live anymore", "I can't go on"
        ).forEach { assertTrue("should flag: $it", CrisisSupport.needsSupport(it)) }
    }

    @Test fun ignoresOrdinaryText() {
        listOf(
            "I had a stressful day at work", "I'm so tired and low today", "My exam killed my mood",
            "We watched a film about the end of the world", ""
        ).forEach { assertFalse("should not flag: $it", CrisisSupport.needsSupport(it)) }
    }

    @Test fun isCaseInsensitive() = assertTrue(CrisisSupport.needsSupport("I WANT TO DIE"))

    @Test fun helplinesIncludeIndiaAndEmergency() {
        assertTrue(helplines.any { it.dial == "14416" })
        assertTrue(helplines.any { it.dial == "112" })
        assertTrue(CrisisSupport.chatMessage.contains("14416"))
    }
}

class CompanionContextTest {
    @Test fun emptyHistoryGivesEmptyContext() = assertEquals("", buildCompanionContext(emptyList(), 0))

    @Test fun includesOnlyRatingsAndNeverJournalText() {
        val ctx = buildCompanionContext(listOf(rec("2026-10-20", 4, 2, 5, journal = "very private words")), 3)
        assertTrue(ctx.contains("2026-10-20"))
        assertTrue(ctx.contains("streak: 3"))
        assertFalse(ctx.contains("very private words"))
    }

    @Test fun keepsOnlyTheLastSevenCheckIns() {
        val records = (1..10).map { rec("2026-10-%02d".format(it)) }
        val lines = buildCompanionContext(records, 1).lines().filter { it.startsWith("2026") }
        assertEquals(7, lines.size)
        assertEquals("2026-10-04", lines.first().substringBefore(":"))
    }
}

class ReportStatsTest {
    @Test fun emptyInputGivesNull() = assertNull(buildReportStats(emptyList()))

    @Test fun computesAveragesAndCounts() {
        val stats = buildReportStats(
            listOf(rec("2026-10-02", 5, 1, 5), rec("2026-10-01", 1, 5, 1), rec("2026-10-03", 3, 3, 3))
        )!!
        assertEquals("2026-10-01", stats.records.first().date)   // sorted oldest first
        assertEquals(3.0, stats.averageMood, 0.001)
        assertEquals(3, stats.daysCovered)
        assertEquals(100, stats.consistencyPercent)
        assertEquals(1, stats.moodCounts[5])
        assertEquals(0, stats.moodCounts[4])
        assertEquals(1, stats.highStressDays)
        assertEquals(1, stats.lowMoodDays)
        assertEquals(1, stats.lowEnergyDays)
    }

    @Test fun longestStreakAndConsistencyWithGaps() {
        val stats = buildReportStats(
            listOf(rec("2026-10-01"), rec("2026-10-02"), rec("2026-10-03"), rec("2026-10-06"))
        )!!
        assertEquals(3, stats.longestStreak)
        assertEquals(6, stats.daysCovered)
        assertEquals(66, stats.consistencyPercent)
    }

    @Test fun moodChangeNeedsAtLeastFourEntries() {
        assertNull(buildReportStats(listOf(rec("2026-10-01"), rec("2026-10-02"), rec("2026-10-03")))!!.moodChange)
        val rising = buildReportStats((1..6).map { rec("2026-10-%02d".format(it), mood = if (it <= 3) 2 else 4) })!!
        assertEquals(2.0, rising.moodChange!!, 0.001)
    }

    @Test fun weekdayAveragesGroupByDay() {
        // 2026-10-19 and 2026-10-26 are Mondays.
        val stats = buildReportStats(listOf(rec("2026-10-19", 2), rec("2026-10-26", 4), rec("2026-10-20", 5)))!!
        assertEquals(3.0, stats.weekdayMood[DayOfWeek.MONDAY]!!, 0.001)
        assertEquals(5.0, stats.weekdayMood[DayOfWeek.TUESDAY]!!, 0.001)
        assertNull(stats.weekdayMood[DayOfWeek.SUNDAY])
    }

    @Test fun observationsNeverMakeClinicalClaims() {
        val stats = buildReportStats((1..20).map { rec("2026-10-%02d".format(it), mood = 1, stress = 5) })!!
        val text = describeObservations(stats).joinToString(" ").lowercase()
        assertNotNull(text)
        listOf("depress", "anxiety disorder", "diagnos").forEach { assertFalse("must not contain $it", text.contains(it)) }
        assertTrue(text.contains("stress was rated 4 or 5 on 20 of 20"))
    }
}

class PrivacyAndFormatTest {
    @Test fun formatScoreHandlesNull() {
        assertEquals("—", formatScore(null))
        assertEquals("3.5", java.util.Locale.setDefault(java.util.Locale.US).let { formatScore(3.5) })
    }

    @Test fun policyCoversAiAndDeletion() {
        val all = privacyPolicy.joinToString(" ") { it.title + " " + it.body }.lowercase()
        assertTrue(all.contains("gemini"))
        assertTrue(all.contains("delete your account"))
        assertTrue(all.contains("not medical advice") || all.contains("not a diagnosis"))
    }
}
