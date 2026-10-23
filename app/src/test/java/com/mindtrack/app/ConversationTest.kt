package com.mindtrack.app

import java.io.File
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day

    private fun msg(text: String, user: Boolean, daysAgo: Double) =
        ChatMessage(if (user) "You" else "MindTrack", text, user, now - (daysAgo * day).toLong())

    private fun chat(id: String, vararg messages: ChatMessage) = Conversation(id, messages.toList())

    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("chats").toFile()

    @After fun resetLanguage() { Lang.current = AppLanguage.ENGLISH }

    // ---- titles ----

    @Test fun titleComesFromTheFirstThingThePersonSaid() {
        val title = conversationTitle(listOf(msg("Hello there", false, 2.0), msg("I've been stressed about exams", true, 2.0)))
        assertEquals("I've been stressed about exams", title)
    }

    @Test fun longTitlesAreShortenedAndTidied() {
        val title = conversationTitle(listOf(msg("  word ".repeat(30), true, 1.0)))
        assertTrue(title.length <= 41)
        assertTrue(title.endsWith("…"))
        assertFalse(title.contains("  "))
    }

    @Test fun aChatWithoutUserMessagesIsCalledNewChat() {
        assertEquals("New chat", conversationTitle(emptyList()))
        assertEquals("New chat", conversationTitle(listOf(msg("welcome", false, 1.0))))
    }

    // ---- deleting after 7 days ----

    @Test fun aChatIsDeletedSevenDaysAfterItsLastMessage() {
        val old = chat("1", msg("a", true, 12.0), msg("b", false, 7.5))
        val keptAlive = chat("2", msg("a", true, 12.0), msg("b", false, 6.5))   // talked recently, so the whole chat stays
        val fresh = chat("3", msg("a", true, 0.1))
        assertEquals(listOf("2", "3"), pruneConversations(listOf(old, keptAlive, fresh), now).map { it.id })
    }

    @Test fun daysUntilDeletedCountsDownFromSeven() {
        assertEquals(7, daysUntilDeleted(now, now))
        assertEquals(6, daysUntilDeleted(now - day, now))
        assertEquals(1, daysUntilDeleted(now - (6.5 * day).toLong(), now))
        assertEquals(0, daysUntilDeleted(now - 8 * day, now))
    }

    @Test fun daysAgoCountsCalendarDays() {
        val zone = ZoneId.of("UTC")
        val noon = 50 * day + day / 2
        assertEquals(0, daysAgo(noon, noon + 1_000, zone))
        assertEquals(1, daysAgo(noon, noon + day, zone))
        assertEquals(3, daysAgo(noon, noon + 3 * day, zone))
    }

    // ---- storage ----

    @Test fun eachChatIsSavedLoadedAndDeletedOnItsOwn() {
        val store = ConversationStore(tempDir())
        store.save(chat("10", msg("first", true, 2.0)))
        store.save(chat("20", msg("second", true, 1.0), msg("reply", false, 1.0)))
        val all = store.loadAll(now)
        assertEquals(listOf("20", "10"), all.map { it.id })          // newest first
        assertEquals(2, all.first().messages.size)
        store.delete("10")
        assertEquals(listOf("20"), store.loadAll(now).map { it.id })
        store.clearAll()
        assertTrue(store.loadAll(now).isEmpty())
    }

    @Test fun expiredChatsAreRemovedFromDiskWhenLoaded() {
        val dir = tempDir()
        val store = ConversationStore(dir)
        store.save(chat("1", msg("ancient", true, 9.0)))
        store.save(chat("2", msg("recent", true, 1.0)))
        assertEquals(listOf("2"), store.loadAll(now).map { it.id })
        assertFalse(File(dir, "1.txt").exists())
    }

    @Test fun anEmptyChatIsNeverWritten() {
        val dir = tempDir()
        val store = ConversationStore(dir)
        store.save(chat("5", ChatMessage("MindTrack", "welcome", false, 0L)))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun oddIdsCannotEscapeTheFolder() {
        val dir = tempDir()
        val store = ConversationStore(dir)
        store.save(chat("../evil", msg("x", true, 1.0)))
        store.delete("../evil")
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test fun theOldSingleChatFileBecomesTheFirstSavedChat() {
        val dir = tempDir()
        val legacy = File.createTempFile("chat_old", ".txt")
        legacy.writeText(serializeChat(listOf(msg("from before the sidebar", true, 2.0), msg("ok", false, 2.0))))
        val store = ConversationStore(dir, legacy)
        val all = store.loadAll(now)
        assertEquals(1, all.size)
        assertEquals("from before the sidebar", conversationTitle(all.first().messages))
        assertFalse(legacy.exists())
        assertEquals(1, store.loadAll(now).size)   // and it is not imported twice
    }

    // ---- words ----

    @Test fun sidebarWordsHaveHindi() {
        Lang.current = AppLanguage.HINDI
        listOf("Chats", "Today", "Yesterday", "Deletes today", "Deletes tomorrow", "Delete all chats", "Delete this chat?").forEach {
            assertNotEquals("No Hindi for: $it", it, tr(it))
        }
        assertTrue(trf("Deletes in %1\$d days", 4).contains("4"))
        assertTrue(trf("%1\$d days ago", 3).contains("3"))
    }
}
