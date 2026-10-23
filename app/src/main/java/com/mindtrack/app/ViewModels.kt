package com.mindtrack.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Holds everything the signed-in app shows: today's draft, the saved history and the numbers derived from it.
 * Composables only read this state and call its functions; storage and cloud sync stay behind [CheckInRepository].
 */
internal class MindTrackViewModel(app: Application, private val uid: String?) : AndroidViewModel(app) {

    private val firebase = FirebaseRepository()
    private val prefs = userPrefs(app, uid)
    val repo = CheckInRepository(prefs, firebase)

    // Today's draft. The journal is a state object so typing recomposes only the text field.
    var mood by mutableIntStateOf(3)
    var stress by mutableIntStateOf(3)
    var energy by mutableIntStateOf(3)
    var tags by mutableStateOf<List<String>>(emptyList())
    val journal = mutableStateOf("")
    var checkedIn by mutableStateOf(false)
        private set

    // History and derived numbers.
    var allRecords by mutableStateOf<List<CheckInRecord>>(emptyList())
        private set
    var recentRecords by mutableStateOf<List<CheckInRecord>>(emptyList())
        private set
    var streak by mutableIntStateOf(0)
        private set
    var weekly by mutableStateOf(weeklySummaryOf(emptyList(), LocalDate.now()))
        private set
    var dismissed by mutableStateOf<Set<String>>(emptySet())
        private set

    // Feedback to the UI.
    var isSyncing by mutableStateOf(false)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set
    var syncOk by mutableStateOf(false)
        private set
    var showSupport by mutableStateOf(false)

    private var loadedDay: LocalDate = LocalDate.now()

    init {
        loadToday()
        refresh()
        viewModelScope.launch {
            // Order matters: send local changes first so an import can never resurrect a deleted day.
            syncPending()
            try {
                if (repo.importFromCloud() > 0) {
                    loadToday()
                    refresh()
                }
            } catch (_: Exception) {
                // Offline or signed out: the on-device history is already showing.
            }
        }
    }

    private fun today() = LocalDate.now()

    private fun loadToday() {
        val day = today()
        val draft = repo.draftFor(day.toString())
        mood = draft.mood; stress = draft.stress; energy = draft.energy
        tags = draft.tags
        journal.value = draft.journal
        checkedIn = repo.hasCheckIn(day.toString())
        loadedDay = day
    }

    /** Call when the app returns to the foreground: after midnight, today's draft must start fresh. */
    fun onResume() {
        if (today() != loadedDay) {
            loadToday()
            refresh()
        }
    }

    fun refresh() {
        val now = today()
        val all = repo.all()
        allRecords = all
        val cutoff = now.minusDays(6)
        recentRecords = all.filter { !LocalDate.parse(it.date).isBefore(cutoff) }.sortedBy { it.date }
        streak = streakFrom(all.map { LocalDate.parse(it.date) }.toSet(), now)
        weekly = weeklySummaryOf(all, now)
        dismissed = dismissedInsights(userPrefs(getApplication(), uid))
    }

    fun toggleTag(tag: String) {
        tags = when {
            tag in tags -> tags - tag
            tags.size >= MAX_TAGS -> tags
            else -> tags + tag
        }
    }

    fun saveToday() {
        val date = today().toString()
        val note = journal.value.trim().take(2000)
        repo.save(CheckInRecord(date, mood, stress, energy, note, tags))
        settingsPrefs(getApplication()).edit { putString(KEY_LAST_CHECKIN, date) }
        recordCheckInTime(settingsPrefs(getApplication()), java.time.LocalTime.now())
        ReminderScheduler.rescheduleFromSettings(getApplication())
        if (CrisisSupport.needsAnySupport(note)) showSupport = true
        checkedIn = true
        refresh()
        pushAfterChange(date, tr("Check-in saved on this device and synced to your account."))
    }

    fun editRecord(date: String, note: String, newTags: List<String>) {
        val existing = repo.get(date) ?: return
        val cleanNote = note.trim().take(2000)
        repo.save(existing.copy(journal = cleanNote, tags = newTags))
        if (date == today().toString()) {
            journal.value = cleanNote
            tags = newTags
        }
        if (CrisisSupport.needsAnySupport(cleanNote)) showSupport = true
        refresh()
        pushAfterChange(date, tr("Entry updated and synced to your account."))
    }

    /** Adds a missed day (see [canBackfill]). Returns false if the date isn't allowed. */
    fun backfill(date: java.time.LocalDate, mood: Int, stress: Int, energy: Int, note: String, tags: List<String>): Boolean {
        if (!canBackfill(date, today(), allRecords.map { it.date }.toSet())) return false
        val cleanNote = note.trim().take(2000)
        repo.save(CheckInRecord(date.toString(), mood.coerceIn(1, 5), stress.coerceIn(1, 5), energy.coerceIn(1, 5), cleanNote, tags.take(MAX_TAGS)))
        if (CrisisSupport.needsAnySupport(cleanNote)) showSupport = true
        refresh()
        pushAfterChange(date.toString(), tr("Entry added and synced to your account."))
        return true
    }

    fun deleteRecord(date: String) {
        repo.delete(date)
        if (date == today().toString()) {
            mood = 3; stress = 3; energy = 3; tags = emptyList(); journal.value = ""
            checkedIn = false
            settingsPrefs(getApplication()).edit { remove(KEY_LAST_CHECKIN) }
        }
        refresh()
        pushAfterChange(date, tr("Entry deleted."))
    }

    fun dismissInsight(id: String) {
        repo.dismissInsight(id)
        refresh()
    }

    private fun pushAfterChange(date: String, success: String) {
        isSyncing = true
        syncOk = false
        syncMessage = tr("Saved on this device. Syncing to your account…")
        viewModelScope.launch {
            syncOk = repo.pushToCloud(date)
            syncMessage = if (syncOk) success
            else tr("Saved on this device. It will sync automatically when you're back online.")
            isSyncing = false
        }
    }

    suspend fun syncPending() {
        repo.syncPending()
    }

    suspend fun reauthenticateWithPassword(password: String) = firebase.reauthenticate(password)

    fun signOut() = firebase.signOut()

    /** Clears what is held in memory for this person (their data stays on disk and in the cloud). */
    fun wipe() {
        allRecords = emptyList(); recentRecords = emptyList(); streak = 0; dismissed = emptySet()
        weekly = weeklySummaryOf(emptyList(), LocalDate.now())
        mood = 3; stress = 3; energy = 3; tags = emptyList(); journal.value = ""
        checkedIn = false; syncMessage = null; showSupport = false
    }

    /** Returns an error message, or null when the account is gone. */
    suspend fun deleteAccount(reauthenticate: suspend () -> Unit): String? = try {
        withTimeout(40_000) {
            reauthenticate()
            firebase.deleteCloudData()
        }
        // Local cleanup happens before the sign-in account disappears and this screen leaves composition.
        val app = getApplication<Application>()
        ReminderScheduler.cancel(app)
        settingsPrefs(app).edit { remove("reminder_enabled"); remove(KEY_LAST_CHECKIN) }
        repo.clearLocal()
        ChatStore.forUser(app, uid).clear()
        firebase.deleteAuthUser()
        null
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        tr("This is taking too long. Check your connection and try again.")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: GoogleSignInProblem) {
        e.message
    } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
        tr("That password isn't right.")
    } catch (e: Exception) {
        e.localizedMessage ?: tr("Couldn't delete the account. Please try again.")
    }
}

/** The companion conversation. Lives in a ViewModel so it survives tab switches and a reply keeps streaming. */
internal class CompanionViewModel(app: Application, uid: String?) : AndroidViewModel(app) {

    private val quota = AiQuota.forContext(app)
    private val store = ConversationStore.forUser(app, uid)
    private var companion = GeminiCompanion(quota)
    private var checkInContext = ""
    private var job: Job? = null

    /** The welcome message first, then the messages of the chat that is open. */
    val messages = mutableStateListOf<ChatMessage>()
    var isSending by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Saved chats for the sidebar, newest first. They live on this device and are deleted 7 days after the last message. */
    val conversations = mutableStateListOf<ConversationSummary>()

    /** The saved chat that is open, or null for a new chat that has no messages yet. */
    var currentId by mutableStateOf<String?>(null)
        private set

    /** Estimated companion messages left today, and when they refresh. Both are estimates. */
    var remaining by mutableIntStateOf(quota.remaining())
        private set
    var resetLabel by mutableStateOf(quota.resetLabel())
        private set

    // Declared before init: opening a saved chat in init sets it.
    /** The last message that failed to send, so it can be retried without typing it again. */
    var failedMessage by mutableStateOf<String?>(null)
        private set

    init {
        messages.add(welcomeMessage())
        // Pick up where the person left off: the most recent chat opens first.
        val all = store.loadAll()
        showList(all)
        all.firstOrNull()?.let { show(it) }
    }

    private fun showList(all: List<Conversation>) {
        conversations.clear()
        conversations.addAll(all.map { it.summary() })
    }

    private fun refreshConversations() = showList(store.loadAll())

    private fun show(conversation: Conversation) {
        currentId = conversation.id
        messages.clear()
        messages.add(welcomeMessage())
        messages.addAll(conversation.messages)
        companion = GeminiCompanion(quota, checkInContext)
        companion.restoreHistory(toMemoryTurns(conversation.messages))
        errorMessage = null
        failedMessage = null
    }

    /** Back to an empty chat. Anything already said stays saved in its own chat. */
    private fun startBlank() {
        currentId = null
        companion = GeminiCompanion(quota, checkInContext)
        messages.clear()
        messages.add(welcomeMessage())
        errorMessage = null
        failedMessage = null
    }

    private fun persist() {
        val real = messages.filter { !it.isWelcome }
        if (real.isNotEmpty()) {
            val id = currentId ?: real.minOf { it.time }.toString().also { currentId = it }
            store.save(Conversation(id, real))
        }
        refreshConversations()
    }

    /** Call when the app returns to the foreground: chats whose last message is over a week old are deleted. */
    fun onResume() {
        refreshConversations()
        val id = currentId
        if (id != null && !isSending && conversations.none { it.id == id }) startBlank()
    }

    fun openConversation(id: String) {
        if (isSending || id == currentId) return
        store.loadAll().firstOrNull { it.id == id }?.let { show(it) }
    }

    fun deleteConversation(id: String) {
        if (id == currentId) { job?.cancel(); isSending = false; startBlank() }
        store.delete(id)
        refreshConversations()
    }

    fun deleteAllConversations() {
        job?.cancel()
        isSending = false
        store.clearAll()
        startBlank()
        refreshConversations()
    }

    fun refreshQuota() {
        remaining = quota.remaining()
        resetLabel = quota.resetLabel()
    }

    fun updateContext(records: List<CheckInRecord>, streak: Int) {
        checkInContext = buildCompanionContext(records, streak)
        companion.checkInContext = checkInContext
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || isSending) return
        messages.add(ChatMessage(tr("You"), message, true))
        if (CrisisSupport.needsSupport(message)) {
            // Always show real-world help, whatever the AI replies.
            messages.add(ChatMessage("MindTrack", CrisisSupport.chatMessage, false))
        } else if (CrisisSupport.needsAbuseSupport(message)) {
            messages.add(ChatMessage("MindTrack", CrisisSupport.abuseChatMessage, false))
        } else {
            // "hi", "thanks" and the like are answered here, so they cost no AI quota.
            trivialReply(message)?.let { reply ->
                messages.add(ChatMessage("MindTrack", reply, false))
                companion.addLocalTurn(message, reply)
                persist()
                return
            }
        }
        request(message)
    }

    fun retry() {
        val message = failedMessage ?: return
        if (isSending) return
        request(message)
    }

    fun newChat() {
        if (isSending) return
        startBlank()
    }

    /** On sign-out: clear what is in memory only. The saved chats stay for the next sign-in of this account. */
    fun wipe() {
        job?.cancel()
        companion = GeminiCompanion(quota)
        checkInContext = ""
        messages.clear()
        messages.add(welcomeMessage())
        conversations.clear()
        currentId = null
        isSending = false
        errorMessage = null
        failedMessage = null
    }

    private fun request(message: String) {
        isSending = true
        errorMessage = null
        failedMessage = null
        job = viewModelScope.launch {
            messages.add(ChatMessage("MindTrack", "", false))
            val index = messages.lastIndex
            try {
                companion.sendMessageStream(message) { chunk ->
                    val current = messages[index]
                    messages[index] = current.copy(
                        text = if (chunk.startsWith("\u0000")) chunk.removePrefix("\u0000") else current.text + chunk
                    )
                }
                if (messages[index].text.isBlank()) {
                    messages[index] = ChatMessage("MindTrack", tr("I'm here with you. Could you tell me a little more?"), false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MindTrackGemini", "Streaming request failed", e)
                if (index in messages.indices && messages[index].text.isBlank()) messages.removeAt(index)
                errorMessage = friendlyAiError(e)
                // Retrying can't help when today's allowance is gone.
                failedMessage = if (e is QuotaExhaustedException) null else message
            } finally {
                isSending = false
                persist()
                refreshQuota()
            }
        }
    }

    private fun welcomeMessage() = ChatMessage(
        sender = "MindTrack",
        text = tr("Hey, I'm glad you're here. This is your space to pause, reflect, and talk things through. How has your day been?"),
        isUser = false,
        time = 0L
    )
}
