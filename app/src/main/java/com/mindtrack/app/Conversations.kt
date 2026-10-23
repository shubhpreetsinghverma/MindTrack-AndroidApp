package com.mindtrack.app

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** One saved chat with the companion. It is deleted [CHAT_RETENTION_DAYS] days after its last message. */
internal data class Conversation(val id: String, val messages: List<ChatMessage>) {
    val lastActivity: Long get() = messages.maxOfOrNull { it.time } ?: 0L
}

/** What the sidebar needs to show a chat. */
internal data class ConversationSummary(val id: String, val title: String, val lastActivity: Long)

internal fun Conversation.summary() = ConversationSummary(id, conversationTitle(messages), lastActivity)

private const val TITLE_LIMIT = 40

/** A chat is named after what the person first said, so nothing extra is sent to the AI. */
internal fun conversationTitle(messages: List<ChatMessage>): String {
    val first = messages.firstOrNull { it.isUser && !it.isWelcome && it.text.isNotBlank() }?.text
        ?: return "New chat"
    val clean = first.trim().replace(Regex("\\s+"), " ")
    return if (clean.length <= TITLE_LIMIT) clean else clean.take(TITLE_LIMIT).trimEnd() + "…"
}

/** Drops whole chats whose last message is older than the retention period. */
internal fun pruneConversations(list: List<Conversation>, now: Long, days: Long = CHAT_RETENTION_DAYS): List<Conversation> {
    val cutoff = now - TimeUnit.DAYS.toMillis(days)
    return list.filter { c -> c.messages.any { !it.isWelcome } && c.lastActivity >= cutoff }
}

/** Whole days until the chat is deleted: 7 right after a message, 0 on its last day. */
internal fun daysUntilDeleted(lastActivity: Long, now: Long, days: Long = CHAT_RETENTION_DAYS): Int {
    val left = lastActivity + TimeUnit.DAYS.toMillis(days) - now
    return if (left <= 0) 0 else TimeUnit.MILLISECONDS.toDays(left + TimeUnit.DAYS.toMillis(1) - 1).toInt().coerceIn(0, days.toInt())
}

/** Calendar days between the last message and now (0 is today), in the person's own time zone. */
internal fun daysAgo(lastActivity: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    val then = Instant.ofEpochMilli(lastActivity).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(then, today).toInt().coerceAtLeast(0)
}

/** Chats are kept as one file each, on this device only. */
internal class ConversationStore(private val dir: File, private val legacyFile: File? = null) {

    private fun file(id: String) = File(dir, "$id.txt")
    private fun validId(id: String) = id.isNotEmpty() && id.all(Char::isDigit)

    /** Every saved chat, newest first. Expired chats are deleted as they are found. */
    fun loadAll(now: Long = System.currentTimeMillis()): List<Conversation> {
        migrateLegacy()
        val all = dir.listFiles { f -> f.extension == "txt" && validId(f.nameWithoutExtension) }.orEmpty().mapNotNull { f ->
            runCatching { Conversation(f.nameWithoutExtension, deserializeChat(f.readText())) }.getOrNull()
        }
        val kept = pruneConversations(all, now)
        all.filter { it !in kept }.forEach { delete(it.id) }
        return kept.sortedByDescending { it.lastActivity }
    }

    fun save(conversation: Conversation) {
        if (!validId(conversation.id)) return
        runCatching {
            val real = conversation.messages.filter { !it.isWelcome && it.text.isNotBlank() }
            if (real.isEmpty()) { file(conversation.id).delete(); return }
            dir.mkdirs()
            file(conversation.id).writeText(serializeChat(real))
        }
    }

    fun delete(id: String) { if (validId(id)) runCatching { file(id).delete() } }

    fun clearAll() { runCatching { dir.listFiles()?.forEach { it.delete() } } }

    /** The single chat file from before there was a sidebar becomes the first saved chat. */
    private fun migrateLegacy() {
        val old = legacyFile?.takeIf { it.exists() } ?: return
        runCatching {
            val messages = deserializeChat(old.readText())
            if (messages.isNotEmpty()) save(Conversation(messages.minOf { it.time }.toString(), messages))
        }
        old.delete()
    }

    companion object {
        fun forUser(app: android.app.Application, uid: String?): ConversationStore {
            val name = uid ?: "anon"
            return ConversationStore(File(app.filesDir, "chats_$name"), File(app.filesDir, "chat_$name.txt"))
        }
    }
}
