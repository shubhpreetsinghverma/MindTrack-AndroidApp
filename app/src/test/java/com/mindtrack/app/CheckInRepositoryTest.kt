package com.mindtrack.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckInRepositoryTest {
    private val prefs = FakePrefs()
    private val cloud = FakeCloud()
    private val repo = CheckInRepository(prefs, cloud)

    private fun rec(date: String, mood: Int = 4, note: String = "", tags: List<String> = emptyList()) =
        CheckInRecord(date, mood, 2, 3, note, tags)

    @Test fun savedEntriesAreOnThePendingListUntilTheCloudConfirms() = runBlocking {
        repo.save(rec("2026-10-20"))
        assertEquals(setOf("2026-10-20"), pendingSyncDates(prefs))
        assertTrue(repo.pushToCloud("2026-10-20"))
        assertTrue(pendingSyncDates(prefs).isEmpty())
        assertEquals(4, cloud.docs.getValue("2026-10-20")["mood"])
    }

    @Test fun anOfflineSaveStaysPendingAndIsSentLater() = runBlocking {
        cloud.offline = true
        repo.save(rec("2026-10-20", note = "written offline"))
        assertFalse(repo.pushToCloud("2026-10-20"))
        assertEquals(setOf("2026-10-20"), pendingSyncDates(prefs))
        assertTrue(cloud.docs.isEmpty())

        cloud.offline = false
        repo.syncPending()
        assertTrue(pendingSyncDates(prefs).isEmpty())
        assertEquals("written offline", cloud.docs.getValue("2026-10-20")["journal"])
    }

    @Test fun editingAnEntryAfterAnOfflineSaveSendsTheLatestVersion() = runBlocking {
        cloud.offline = true
        repo.save(rec("2026-10-20", note = "first"))
        repo.pushToCloud("2026-10-20")
        repo.save(rec("2026-10-20", note = "second"))
        cloud.offline = false
        repo.syncPending()
        assertEquals("second", cloud.docs.getValue("2026-10-20")["journal"])
    }

    @Test fun deletingRemovesItLocallyAndTheCloudCopyFollows() = runBlocking {
        repo.save(rec("2026-10-20")); repo.pushToCloud("2026-10-20")
        repo.delete("2026-10-20")
        assertNull(repo.get("2026-10-20"))
        assertEquals(setOf("2026-10-20"), pendingSyncDates(prefs))
        repo.syncPending()
        assertTrue(cloud.docs.isEmpty())
        assertTrue(pendingSyncDates(prefs).isEmpty())
    }

    @Test fun aDeleteMadeOfflineIsNotBroughtBackByAnImport() = runBlocking {
        repo.save(rec("2026-10-20")); repo.pushToCloud("2026-10-20")
        cloud.offline = true
        repo.delete("2026-10-20")
        repo.pushToCloud("2026-10-20")
        cloud.offline = false

        assertEquals(0, repo.importFromCloud())          // the cloud still has it, but it is pending deletion
        assertNull(repo.get("2026-10-20"))
        repo.syncPending()
        assertTrue(cloud.docs.isEmpty())
    }

    @Test fun importAddsMissingDaysButNeverOverwritesLocalOnes() = runBlocking {
        repo.save(rec("2026-10-19", mood = 5, note = "mine"))
        cloud.docs["2026-10-19"] = mapOf("date" to "2026-10-19", "mood" to 1, "stress" to 1, "energy" to 1, "journal" to "theirs")
        cloud.docs["2026-10-18"] = mapOf("date" to "2026-10-18", "mood" to 2, "stress" to 4, "energy" to 1, "journal" to "from cloud", "tags" to listOf("Poor sleep", "Not a real tag"))

        assertEquals(1, repo.importFromCloud())
        assertEquals("mine", repo.get("2026-10-19")!!.journal)
        val imported = repo.get("2026-10-18")!!
        assertEquals("from cloud", imported.journal)
        assertEquals(listOf("Poor sleep"), imported.tags)          // unknown tags are dropped
    }

    @Test fun importSkipsIncompleteCloudDocuments() = runBlocking {
        cloud.docs["2026-10-17"] = mapOf("date" to "2026-10-17", "mood" to 3)
        assertEquals(0, repo.importFromCloud())
    }

    @Test fun tagsAreFilteredAndLimitedWhenSaved() {
        repo.save(rec("2026-10-20", tags = listOf("Money", "Nonsense") + moodTags.take(10)))
        val saved = repo.get("2026-10-20")!!
        assertTrue(saved.tags.all { it in moodTags })
        assertTrue(saved.tags.size <= MAX_TAGS)
    }

    @Test fun theDraftForADayRestoresWhatWasSaved() {
        repo.save(rec("2026-10-20", mood = 2, note = "heavy day", tags = listOf("Family")))
        val draft = repo.draftFor("2026-10-20")
        assertEquals(2, draft.mood)
        assertEquals("heavy day", draft.journal)
        assertEquals(listOf("Family"), draft.tags)
        assertEquals(3, repo.draftFor("2026-10-01").mood)           // untouched day: defaults
    }

    @Test fun allRecordsComeBackNewestFirstAndFeedTheStreak() {
        listOf("2026-10-18", "2026-10-20", "2026-10-19").forEach { repo.save(rec(it)) }
        assertEquals(listOf("2026-10-20", "2026-10-19", "2026-10-18"), repo.all().map { it.date })
        assertEquals(3, computeStreak(prefs, LocalDate.of(2026, 10, 20)))
    }

    @Test fun dismissedInsightsAreRemembered() {
        repo.dismissInsight("weekday-mood")
        assertEquals(setOf("weekday-mood"), dismissedInsights(prefs))
    }

    @Test fun clearingLocalDataLeavesNothingBehind() {
        repo.save(rec("2026-10-20")); repo.dismissInsight("x")
        repo.clearLocal()
        assertTrue(repo.all().isEmpty())
        assertTrue(pendingSyncDates(prefs).isEmpty())
    }
}
