package com.mindtrack.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private class MemoryStore : QuotaStore {
    var day: String? = null
    var counts: Map<String, Int> = emptyMap()
    var exhaustedSet: Set<String> = emptySet()
    override fun day() = day
    override fun counts() = counts
    override fun exhausted() = exhaustedSet
    override fun save(day: String, counts: Map<String, Int>, exhausted: Set<String>) {
        this.day = day; this.counts = counts.toMap(); this.exhaustedSet = exhausted.toSet()
    }
}

private val kolkata = ZoneId.of("Asia/Kolkata")
private fun at(date: String, time: String) = ZonedDateTime.parse("${date}T$time:00+05:30[Asia/Kolkata]")

class AiQuotaTest {
    @Test fun startsFullAndCountsDown() {
        val q = AiQuota(MemoryStore()) { at("2026-10-20", "10:00") }
        val full = AiModels.all.size * AiModels.ESTIMATED_DAILY_LIMIT
        assertEquals(full, q.remaining())
        q.record(AiModels.PRIMARY)
        assertEquals(full - 1, q.remaining())
    }

    @Test fun movesToTheFallbackModelWhenThePrimaryIsUsedUp() {
        val q = AiQuota(MemoryStore()) { at("2026-10-20", "10:00") }
        repeat(AiModels.ESTIMATED_DAILY_LIMIT) { q.record(AiModels.PRIMARY) }
        assertEquals(listOf(AiModels.FALLBACK), q.availableModels())
    }

    @Test fun serverSaysExhaustedEvenIfOurCountIsLow() {
        val q = AiQuota(MemoryStore()) { at("2026-10-20", "10:00") }
        q.markExhausted(AiModels.PRIMARY)
        assertFalse(AiModels.PRIMARY in q.availableModels())
        assertEquals(AiModels.ESTIMATED_DAILY_LIMIT, q.remaining())   // only the fallback is left
    }

    @Test fun nothingLeftWhenEveryModelIsUsedUp() {
        val q = AiQuota(MemoryStore()) { at("2026-10-20", "10:00") }
        AiModels.all.forEach { q.markExhausted(it) }
        assertEquals(0, q.remaining())
        assertTrue(q.availableModels().isEmpty())
    }

    @Test fun resetsAtMidnightPacificNotLocalMidnight() {
        val store = MemoryStore()
        var now = at("2026-10-20", "11:00")            // 22:30 on Oct 19 in Pacific time
        val q = AiQuota(store) { now }
        AiModels.all.forEach { q.markExhausted(it) }
        now = at("2026-10-20", "12:00")                // 23:30 Pacific: still the same quota day
        assertEquals(0, q.remaining())
        now = at("2026-10-20", "13:00")                // 00:30 Pacific on Oct 20: new quota day
        assertEquals(AiModels.all.size * AiModels.ESTIMATED_DAILY_LIMIT, q.remaining())
    }

    @Test fun resetLabelInIndiaDuringDaylightSavingIsHalfPastTwelve() {
        assertEquals("12:30 PM", nextQuotaResetLabel(at("2026-10-20", "04:00"), Locale.US))
    }

    @Test fun resetLabelAfterDaylightSavingEndsIsHalfPastOne() {
        assertEquals("1:30 PM", nextQuotaResetLabel(at("2026-12-10", "04:00"), Locale.US))
    }
}

class TrivialReplyTest {
    @Test fun answersGreetingsThanksAndGoodbyesLocally() {
        listOf("hi", "Hello!", "hey there", "Thanks 😊", "thank you", "Bye", "good night", "ok").forEach {
            assertNotNull("should answer locally: $it", trivialReply(it))
        }
    }

    @Test fun sendsAnythingWithRealContentToTheAi() {
        listOf(
            "hi, I feel awful today", "thanks but I still can't sleep", "I'm anxious about my exam",
            "ok but what should I do", "hello I need to talk"
        ).forEach { assertNull("should go to the AI: $it", trivialReply(it)) }
    }

    @Test fun neverInterceptsCrisisWording() {
        assertNull(trivialReply("I want to die"))
        assertNull(trivialReply("suicide"))
    }

    @Test fun ignoresEmptyAndVeryLongInput() {
        assertNull(trivialReply(""))
        assertNull(trivialReply("   "))
        assertNull(trivialReply("thank you ".repeat(10)))
    }

    @Test fun repliesAreStableForTheSameMessage() {
        assertEquals(trivialReply("hello"), trivialReply("hello"))
    }
}

class SuggestionCacheTest {
    private fun r(day: Int, mood: Int, tags: List<String> = emptyList(), journal: String = "") =
        CheckInRecord("2026-10-%02d".format(day), mood, 3, 3, journal, tags)

    @Test fun sameDayAndSameCheckInsReuseTheSavedAnswer() {
        val sig = SuggestionCache.signature(listOf(r(19, 3), r(20, 4)))
        assertTrue(SuggestionCache.isFresh("2026-10-20", sig, "2026-10-20", sig))
    }

    @Test fun aNewDayNeedsANewRequest() {
        val sig = SuggestionCache.signature(listOf(r(20, 4)))
        assertFalse(SuggestionCache.isFresh("2026-10-19", sig, "2026-10-20", sig))
    }

    @Test fun changedCheckInsNeedANewRequest() {
        val before = SuggestionCache.signature(listOf(r(20, 4)))
        val after = SuggestionCache.signature(listOf(r(20, 2)))
        assertFalse(SuggestionCache.isFresh("2026-10-20", before, "2026-10-20", after))
    }

    @Test fun journalTextNeverAffectsOrEntersTheSignature() {
        val a = SuggestionCache.signature(listOf(r(20, 4, journal = "private words")))
        val b = SuggestionCache.signature(listOf(r(20, 4, journal = "other words")))
        assertEquals(a, b)
        assertFalse(a.contains("private"))
    }

    @Test fun orderOfRecordsDoesNotMatter() {
        assertEquals(
            SuggestionCache.signature(listOf(r(19, 3), r(20, 4))),
            SuggestionCache.signature(listOf(r(20, 4), r(19, 3)))
        )
    }
}
