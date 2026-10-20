package com.mindtrack.app

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** How long the companion keeps your conversation, on this device only. */
internal const val CHAT_RETENTION_DAYS = 7L

/** Most recent messages handed to the AI as memory, so requests stay small. */
internal const val MAX_MEMORY_MESSAGES = 30

internal data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    /** When it was written. 0 marks the built-in welcome message, which is never saved. */
    val time: Long = System.currentTimeMillis()
) {
    val isWelcome: Boolean get() = time == 0L
}

/** Drops messages older than the retention period (and the welcome message). */
internal fun pruneExpired(messages: List<ChatMessage>, now: Long, days: Long = CHAT_RETENTION_DAYS): List<ChatMessage> {
    val cutoff = now - TimeUnit.DAYS.toMillis(days)
    return messages.filter { !it.isWelcome && it.time >= cutoff }
}

/** One message per line: time, who, and the text in Base64 so no character can break the format. */
internal fun serializeChat(messages: List<ChatMessage>): String =
    messages.filter { !it.isWelcome && it.text.isNotBlank() }.joinToString("\n") {
        val sender = Base64.getEncoder().encodeToString(it.sender.toByteArray())
        val text = Base64.getEncoder().encodeToString(it.text.toByteArray())
        "${it.time}|${if (it.isUser) 1 else 0}|$sender|$text"
    }

internal fun deserializeChat(data: String): List<ChatMessage> =
    data.lineSequence().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 4) return@mapNotNull null
        runCatching {
            ChatMessage(
                sender = String(Base64.getDecoder().decode(parts[2])),
                text = String(Base64.getDecoder().decode(parts[3])),
                isUser = parts[1] == "1",
                time = parts[0].toLong()
            )
        }.getOrNull()
    }.toList()

/**
 * The last [MAX_MEMORY_MESSAGES] messages as strictly alternating user/assistant turns, which is what
 * the AI expects: consecutive messages from the same side are joined, and it always starts with the user.
 */
internal fun toMemoryTurns(messages: List<ChatMessage>): List<Pair<Boolean, String>> {
    val turns = mutableListOf<Pair<Boolean, String>>()
    messages.filter { !it.isWelcome && it.text.isNotBlank() }.takeLast(MAX_MEMORY_MESSAGES).forEach { m ->
        if (turns.isNotEmpty() && turns.last().first == m.isUser) {
            turns[turns.lastIndex] = m.isUser to (turns.last().second + "\n\n" + m.text)
        } else turns += m.isUser to m.text
    }
    while (turns.isNotEmpty() && !turns.first().first) turns.removeAt(0)
    return turns
}

internal class ChatStore(private val file: File) {
    fun load(now: Long = System.currentTimeMillis()): List<ChatMessage> =
        runCatching { pruneExpired(deserializeChat(file.readText()), now) }.getOrDefault(emptyList())

    fun save(messages: List<ChatMessage>, now: Long = System.currentTimeMillis()) {
        runCatching {
            val kept = pruneExpired(messages, now)
            if (kept.isEmpty()) file.delete() else file.writeText(serializeChat(kept))
        }
    }

    fun clear() { runCatching { file.delete() } }

    companion object {
        fun forUser(app: android.app.Application, uid: String?) =
            ChatStore(File(app.filesDir, "chat_${uid ?: "anon"}.txt"))
    }
}
