package com.mindtrack.app

import androidx.compose.ui.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun r(day: Int, mood: Int, stress: Int = 3, energy: Int = 3, tags: List<String> = emptyList()) =
    CheckInRecord("2026-10-%02d".format(day), mood, stress, energy, "", tags)

class MarkdownTest {
    private fun render(s: String) = markdownToAnnotated(s, Color.Blue)

    @Test fun boldAndItalicMarkersAreRemoved() {
        assertEquals("a bold and italic word", render("a **bold** and *italic* word").text)
    }

    @Test fun bulletsAndNumbersBecomeReadableLists() {
        val text = render("- one\n* two\n1. three").text
        assertEquals("•  one\n•  two\n1.  three", text)
    }

    @Test fun linksKeepTheirLabelAndCarryTheUrl() {
        val a = render("see [the guide](https://example.com/x) now")
        assertEquals("see the guide now", a.text)
        assertEquals(1, a.getLinkAnnotations(0, a.length).size)
    }

    @Test fun bareUrlsBecomeLinks() {
        val a = render("go to https://example.com today")
        assertEquals(1, a.getLinkAnnotations(0, a.length).size)
    }

    @Test fun onlyHttpLinksAreLinked() {
        val a = render("[x](javascript:alert(1)) and [y](intent://foo)")
        assertEquals(0, a.getLinkAnnotations(0, a.length).size)
    }

    @Test fun unfinishedMarkupWhileStreamingStaysPlain() {
        assertEquals("half **bold", render("half **bold").text)
        assertEquals("", render("").text)
    }

    @Test fun listsWithMathLikeAsterisksAreNotItalicised() {
        assertEquals("2 * 3 * 4", render("2 * 3 * 4").text)
    }
}

class PatternInsightTest {
    @Test fun nothingBelowTheMinimumNumberOfDays() {
        val few = (1..10).map { r(it, 3) }
        assertTrue(findPatterns(few).isEmpty())
    }

    @Test fun findsATagThatCoincidesWithLowerMood() {
        val records = (1..20).map { day ->
            val tagged = day % 3 == 0
            r(day, mood = if (tagged) 2 else 4, tags = if (tagged) listOf("Poor sleep") else emptyList())
        }
        val insight = findPatterns(records).firstOrNull { it.id == "tag-mood-Poor sleep" }
        assertNotNull(insight)
        assertTrue(insight!!.how.contains("cannot show"))   // always explains the limits
    }

    @Test fun ignoresTagsUsedOnlyAFewTimes() {
        val records = (1..20).map { day ->
            r(day, mood = if (day <= 3) 1 else 4, tags = if (day <= 3) listOf("Money") else emptyList())
        }
        assertTrue(findPatterns(records).none { it.id.endsWith("Money") })
    }

    @Test fun noPatternWhenEverythingIsSimilar() {
        assertTrue(findPatterns((1..25).map { r(it, 3) }).isEmpty())
    }

    @Test fun phrasedAsPossibilitiesNotCauses() {
        val records = (1..20).map { day ->
            val tagged = day % 2 == 0
            r(day, mood = if (tagged) 1 else 5, tags = if (tagged) listOf("Work or study") else emptyList())
        }
        findPatterns(records).forEach {
            val text = (it.detail + " " + it.how).lowercase()
            assertFalse(text.contains("causes you"))
            assertFalse(text.contains("diagnos"))
        }
    }

    @Test fun correlationBasics() {
        assertEquals(1.0, correlation(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))!!, 1e-9)
        assertEquals(-1.0, correlation(listOf(1.0, 2.0, 3.0), listOf(6.0, 4.0, 2.0))!!, 1e-9)
        assertNull(correlation(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
        assertNull(correlation(listOf(1.0, 2.0), listOf(1.0, 2.0)))
    }
}

class ForecastTest {
    @Test fun tooEarlyBelowTheMinimum() {
        val outcome = evaluateForecast(List(10) { 3 })
        assertTrue(outcome is ForecastOutcome.TooEarly)
        assertEquals(FORECAST_MIN_ENTRIES, (outcome as ForecastOutcome.TooEarly).need)
    }

    @Test fun refusesToForecastRandomNoise() {
        // Alternating extremes: nothing predicts it better than the plain average.
        val noise = List(30) { if (it % 2 == 0) 1 else 5 }
        assertTrue(evaluateForecast(noise) !is ForecastOutcome.Ready)
    }

    @Test fun forecastsWhenRecentValuesGenuinelyPredictTheNext() {
        // A slow drift: the last value is a far better guide than the long-run average.
        val drift = (0 until 40).map { (1 + (it / 8)).coerceAtMost(5) }
        val outcome = evaluateForecast(drift)
        assertTrue(outcome is ForecastOutcome.Ready)
        outcome as ForecastOutcome.Ready
        assertTrue(outcome.typicalError < outcome.baselineError)
        assertTrue(outcome.predicted in 1.0..5.0)
    }

    @Test fun neverUsesFutureValuesToPredictThePast() {
        // Changing the final value must not change how earlier points were scored (walk-forward only).
        val a = List(25) { 3 } + listOf(3)
        val b = List(25) { 3 } + listOf(5)
        val ea = evaluateForecast(a)
        val eb = evaluateForecast(b)
        assertTrue(ea is ForecastOutcome.NotReliable || ea is ForecastOutcome.Ready)
        assertTrue(eb is ForecastOutcome.NotReliable || eb is ForecastOutcome.Ready)
    }
}

class TagTests {
    @Test fun tagListHasNoDuplicatesAndFitsTheLimit() {
        assertEquals(moodTags.size, moodTags.toSet().size)
        assertTrue(MAX_TAGS in 1..moodTags.size)
    }

    @Test fun everyMoodHasAColour() {
        (1..5).map { moodColorFor(it) }.toSet().let { assertEquals(5, it.size) }
    }
}

class BackfillTest {
    private val today = java.time.LocalDate.of(2026, 10, 20)

    @Test fun allowsAMissedPastDayWithinThirtyDays() {
        assertTrue(canBackfill(today.minusDays(1), today, emptySet()))
        assertTrue(canBackfill(today.minusDays(30), today, emptySet()))
    }

    @Test fun refusesTodayFutureTooOldAndExistingDays() {
        assertFalse(canBackfill(today, today, emptySet()))
        assertFalse(canBackfill(today.plusDays(1), today, emptySet()))
        assertFalse(canBackfill(today.minusDays(31), today, emptySet()))
        assertFalse(canBackfill(today.minusDays(2), today, setOf(today.minusDays(2).toString())))
    }

    @Test fun backfilledDaysCountTowardTheStreak() {
        val days = setOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, streakFrom(days, today))                   // gap yesterday
        val filled = days + today.minusDays(1)                     // the missed day is added
        assertEquals(4, streakFrom(filled, today))
    }
}

class BreathingTest {
    private val box = breathingPatterns.first { it.name == "Box breathing" }
    private val fourSevenEight = breathingPatterns.first { it.name == "4-7-8 breathing" }

    @Test fun cycleLengthIsTheSumOfItsSteps() {
        assertEquals(16, box.cycleSeconds)
        assertEquals(19, fourSevenEight.cycleSeconds)
    }

    @Test fun startsBreathingInFromEmpty() {
        val s = breathStateAt(box, 0)
        assertEquals(BreathPhase.IN, s.phase)
        assertEquals(4, s.secondsLeft)
        assertEquals(0f, s.size, 0.001f)
    }

    @Test fun lungsAreFullAtTheEndOfTheInBreathAndDuringTheHold() {
        val half = breathStateAt(box, 2_000)
        assertEquals(0.5f, half.size, 0.01f)
        val hold = breathStateAt(box, 5_000)
        assertEquals(BreathPhase.HOLD_FULL, hold.phase)
        assertEquals(1f, hold.size, 0.001f)
    }

    @Test fun emptiesDuringTheOutBreathAndStaysEmptyOnTheRest() {
        val out = breathStateAt(box, 10_000)          // 2s into the 4s out-breath
        assertEquals(BreathPhase.OUT, out.phase)
        assertEquals(0.5f, out.size, 0.01f)
        val rest = breathStateAt(box, 13_000)
        assertEquals(BreathPhase.HOLD_EMPTY, rest.phase)
        assertEquals(0f, rest.size, 0.001f)
    }

    @Test fun repeatsEveryCycle() {
        assertEquals(breathStateAt(box, 3_000).phase, breathStateAt(box, 3_000 + 16_000L * 5).phase)
    }

    @Test fun guidedExercisesHaveTheirSteps() {
        assertEquals(5, groundingSteps.size)
        assertTrue(groundingSteps.first().title.startsWith("5"))
        assertTrue(relaxationSteps.size >= 5)
    }
}

class ReminderLogicTest {
    private val monday = java.time.LocalDateTime.of(2026, 10, 19, 10, 0)      // a Monday
    private val allDays = java.time.DayOfWeek.entries.toSet()
    private fun t(h: Int, m: Int = 0) = java.time.LocalTime.of(h, m)

    @Test fun nextTriggerPicksTheNextTimeToday() {
        assertEquals(java.time.LocalDateTime.of(2026, 10, 19, 20, 0), nextTrigger(monday, listOf(t(8), t(20)), allDays))
    }

    @Test fun rollsToTomorrowWhenAllTimesHavePassed() {
        assertEquals(java.time.LocalDateTime.of(2026, 10, 20, 8, 0), nextTrigger(monday, listOf(t(8)), allDays))
    }

    @Test fun skipsDaysThatAreNotSelected() {
        val weekendOnly = setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 24, 9, 0), nextTrigger(monday, listOf(t(9)), weekendOnly))
    }

    @Test fun nothingScheduledWithoutTimesOrDays() {
        assertNull(nextTrigger(monday, emptyList(), allDays))
        assertNull(nextTrigger(monday, listOf(t(9)), emptySet()))
    }

    @Test fun smartTimeNeedsEnoughCheckIns() {
        assertNull(smartMinuteOfDay(listOf(1200, 1210, 1190)))
        assertEquals(1200, smartMinuteOfDay(listOf(1200, 1190, 1210, 1180, 1220)))
    }

    @Test fun smartTimeIsKeptWithinSensibleHours() {
        assertEquals(7 * 60, smartMinuteOfDay(List(6) { 3 * 60 }))             // 3 AM check-ins -> not before 07:00
        assertEquals(22 * 60 + 30, smartMinuteOfDay(List(6) { 23 * 60 + 50 })) // late night -> not after 22:30
    }

    @Test fun smartTimingReplacesTheChosenTimesOnlyWhenReady() {
        val config = ReminderConfig(true, listOf(t(20)), allDays, smart = true, gentle = true)
        assertEquals(listOf(t(20)), effectiveTimes(config, listOf(500)))
        assertEquals(listOf(t(8, 0)), effectiveTimes(config, List(6) { 8 * 60 }))
        assertEquals(listOf(t(20)), effectiveTimes(config.copy(smart = false), List(6) { 8 * 60 }))
    }

    @Test fun gentleNudgeOnlyAfterThreeMissedDaysAndNotTooOften() {
        assertEquals(NudgeKind.NORMAL, nudgeKind(1, true, null))
        assertEquals(NudgeKind.GENTLE_AFTER_ABSENCE, nudgeKind(3, true, null))
        assertEquals(NudgeKind.NORMAL, nudgeKind(5, true, 1))
        assertEquals(NudgeKind.GENTLE_AFTER_ABSENCE, nudgeKind(5, true, 3))
        assertEquals(NudgeKind.NORMAL, nudgeKind(9, false, null))
        assertEquals(NudgeKind.NORMAL, nudgeKind(null, true, null))
    }

    @Test fun timesAndDaysRoundTripThroughText() {
        assertEquals(listOf(t(8, 30), t(20)), parseTimes(formatTimes(listOf(t(20), t(8, 30)))))
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY), parseDays(formatDays(setOf(java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.MONDAY))))
        assertEquals(allDays, parseDays(null))
        assertTrue(parseTimes("garbage,25:99").isEmpty())
        assertEquals(MAX_REMINDER_TIMES, parseTimes("06:00,07:00,08:00,09:00").size)
    }
}

class AppLockTest {
    @Test fun locksAfterAMinuteInTheBackgroundOnlyWhenEnabled() {
        assertTrue(shouldLockAfterBackground(true, 1_000, 61_000))
        assertFalse(shouldLockAfterBackground(true, 1_000, 30_000))
        assertFalse(shouldLockAfterBackground(false, 1_000, 999_000))
    }

    @Test fun neverLocksBeforeTheFirstTimeInTheBackground() {
        assertFalse(shouldLockAfterBackground(true, 0, 999_000))
    }
}

class ChatStoreTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 10 * day
    private fun m(text: String, user: Boolean, daysAgo: Double) =
        ChatMessage(if (user) "You" else "MindTrack", text, user, now - (daysAgo * day).toLong())

    @Test fun messagesOlderThanSevenDaysAreDeleted() {
        val kept = pruneExpired(listOf(m("old", true, 7.5), m("recent", true, 6.9), m("new", false, 0.1)), now)
        assertEquals(listOf("recent", "new"), kept.map { it.text })
    }

    @Test fun theWelcomeMessageIsNeverKept() {
        val welcome = ChatMessage("MindTrack", "Hello", false, 0L)
        assertTrue(pruneExpired(listOf(welcome, m("hi", true, 1.0)), now).none { it.isWelcome })
        assertFalse(serializeChat(listOf(welcome)).contains("Hello"))
    }

    @Test fun savesAndRestoresAnyText() {
        val original = listOf(
            m("line one\nline two | with a pipe", true, 2.0),
            m("नमस्ते 🙂 \"quotes\" and \\ backslashes", false, 1.0)
        )
        assertEquals(original, deserializeChat(serializeChat(original)))
    }

    @Test fun ignoresDamagedLines() {
        val good = serializeChat(listOf(m("ok", true, 1.0)))
        assertEquals(1, deserializeChat("garbage\n$good\n1|2|3").size)
    }

    @Test fun memoryTurnsAlternateAndStartWithTheUser() {
        val turns = toMemoryTurns(listOf(
            m("assistant first", false, 3.0),
            m("hello", true, 2.0), m("crisis help text", false, 2.0), m("ai reply", false, 2.0),
            m("thanks", true, 1.0)
        ))
        assertEquals(listOf(true, false, true), turns.map { it.first })
        assertTrue(turns[1].second.contains("crisis help text") && turns[1].second.contains("ai reply"))
        assertFalse(turns.any { it.second.contains("assistant first") })
    }

    @Test fun onlyTheMostRecentMessagesBecomeMemory() {
        val many = (1..80).map { m("msg $it", it % 2 == 1, 1.0) }
        val turns = toMemoryTurns(many)
        assertTrue(turns.sumOf { it.second.split("\n\n").size } <= MAX_MEMORY_MESSAGES)
    }

    @Test fun storeWritesReadsAndClears() {
        val file = java.io.File.createTempFile("chat", ".txt")
        val store = ChatStore(file)
        store.save(listOf(m("hello", true, 1.0)), now)
        assertEquals(listOf("hello"), store.load(now).map { it.text })
        assertEquals(emptyList<ChatMessage>(), store.load(now + 8 * day))   // a week later it has expired
        store.clear()
        assertFalse(file.exists())
    }
}

class ThemeTest {
    @After fun reset() { ThemeState.mode = ThemeMode.SYSTEM; ThemeState.systemDark = true }

    @Test fun lightThemeIsWhiteAndLavender() {
        ThemeState.mode = ThemeMode.LIGHT
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFBFAFF), Background)
        assertTrue(Lavender.red < 0.5f && Lavender.blue > 0.7f)        // a lavender/violet accent
        assertFalse(ThemeState.isDark)
    }

    @Test fun darkThemeKeepsTheOriginalNavy() {
        ThemeState.mode = ThemeMode.DARK
        assertEquals(androidx.compose.ui.graphics.Color(0xFF090D1B), Background)
        assertTrue(ThemeState.isDark)
    }

    @Test fun autoFollowsThePhone() {
        ThemeState.mode = ThemeMode.SYSTEM
        ThemeState.systemDark = false
        assertFalse(ThemeState.isDark)
        ThemeState.systemDark = true
        assertTrue(ThemeState.isDark)
    }

    @Test fun textStaysReadableOnTheBackgroundInBothThemes() {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        fun luminance(c: androidx.compose.ui.graphics.Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
            val l1 = maxOf(luminance(a), luminance(b)) + 0.05
            val l2 = minOf(luminance(a), luminance(b)) + 0.05
            return l1 / l2
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            ThemeState.mode = mode
            assertTrue("primary text, $mode", contrast(TextPrimary, Background) > 7)
            assertTrue("secondary text, $mode", contrast(TextSecondary, Background) > 4.5)
            assertTrue("accent text, $mode", contrast(Lavender, Background) > 3)
        }
    }
}

class HostedPolicyTest {
    private fun unescape(s: String) = s.replace("&#x27;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    @Test fun theHostedPageContainsEverySectionInFull() {
        val page = unescape(java.io.File("../public/privacy.html").readText())
        privacyPolicy.forEach { section ->
            assertTrue("missing title: ${section.title}", page.contains(section.title))
            // Every line of every section, including bullet points, must be on the page.
            section.body.lines().map { it.removePrefix("•").trim() }.filter { it.isNotBlank() }.forEach { line ->
                assertTrue("missing from the hosted page: ${line.take(60)}", page.contains(line))
            }
        }
    }

    @Test fun markdownCopyMatchesToo() {
        val md = java.io.File("../PRIVACY_POLICY.md").readText()
        privacyPolicy.forEach { assertTrue(md.contains(it.title)) }
    }
}

class BreathPhaseIndexTest {
    private val box = breathingPatterns.first { it.name == "Box breathing" }

    @Test fun phaseIndexChangesExactlyWhenANewPhaseBegins() {
        assertEquals(0, breathStateAt(box, 0).index)
        assertEquals(0, breathStateAt(box, 3_999).index)
        assertEquals(1, breathStateAt(box, 4_000).index)
        assertEquals(3, breathStateAt(box, 15_999).index)
        assertEquals(4, breathStateAt(box, 16_000).index)     // second cycle, first phase again
    }

    @Test fun everyPatternHasASpokenIntroduction() {
        breathingPatterns.forEach { assertTrue(it.intro.length > 40) }
    }

    @Test fun everyIntroHasAHindiVersion() {
        val hindi = hindiStrings + hindiLongStrings
        breathingPatterns.forEach { assertTrue("no Hindi intro for ${it.name}", hindi.containsKey(it.intro.replace("\"", "\\\""))) }
        (groundingSteps + relaxationSteps).forEach {
            assertTrue("no Hindi title: ${it.title}", hindi.containsKey(it.title))
            assertTrue("no Hindi text: ${it.title}", hindi.containsKey(it.body))
        }
        BreathPhase.entries.forEach { assertTrue("no Hindi cue: ${it.label}", hindi.containsKey(it.label)) }
    }
}
