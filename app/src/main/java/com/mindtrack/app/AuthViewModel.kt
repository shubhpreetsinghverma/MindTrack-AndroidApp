package com.mindtrack.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Who is signed in, and the sign-in, sign-up and password-reset actions. The screen only shows this state. */
internal class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()
    private val repository = FirebaseRepository()

    var uid by mutableStateOf(auth.currentUser?.uid)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set

    private val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }

    init {
        auth.addAuthStateListener(listener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
    }

    fun clearMessages() {
        error = null
        info = null
    }

    fun showError(message: String) {
        info = null
        error = message
    }

    private fun validEmail(email: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyAuthError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun signIn(email: String, password: String) {
        clearMessages()
        val cleanEmail = email.trim()
        when {
            cleanEmail.isBlank() -> error = tr("Enter your email address.")
            !validEmail(cleanEmail) -> error = tr("Enter a valid email address.")
            password.length < 6 -> error = tr("Use a password with at least 6 characters.")
            else -> run { auth.signInWithEmailAndPassword(cleanEmail, password).await() }
        }
    }

    fun register(name: String, email: String, password: String) {
        clearMessages()
        val cleanEmail = email.trim()
        when {
            cleanEmail.isBlank() -> error = tr("Enter your email address.")
            !validEmail(cleanEmail) -> error = tr("Enter a valid email address.")
            password.length < 6 -> error = tr("Use a password with at least 6 characters.")
            name.isBlank() -> error = tr("Enter your name.")
            else -> run {
                val user = auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
                    ?: error(tr("Account created, but user details were unavailable."))
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
                repository.saveProfile(name)
                // Best effort: not being able to send this must never block sign-up.
                runCatching { user.sendEmailVerification().await() }
            }
        }
    }

    fun signInWithGoogle(activity: android.app.Activity) {
        clearMessages()
        viewModelScope.launch {
            isLoading = true
            try {
                val token = requestGoogleIdToken(activity)
                val user = auth.signInWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)).await().user
                // First time with Google: keep a small profile record like email sign-up does.
                user?.displayName?.takeIf { it.isNotBlank() }?.let { runCatching { repository.saveProfile(it) } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: GoogleSignInProblem) {
                if (!e.cancelled) error = e.message
            } catch (e: Exception) {
                error = friendlyAuthError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        clearMessages()
        val cleanEmail = email.trim()
        if (!validEmail(cleanEmail)) {
            error = tr("Enter your email address above first, then tap \"Forgot password?\".")
            return
        }
        viewModelScope.launch {
            isLoading = true
            try {
                repository.sendPasswordReset(cleanEmail)
                info = resetInfo(cleanEmail)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is com.google.firebase.FirebaseNetworkException) {
                    error = tr("Network error. Check your internet connection.")
                } else {
                    // Don't reveal whether an address has an account.
                    info = resetInfo(cleanEmail)
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun resetInfo(email: String) =
        trf("If an account exists for %1\$s, a password reset link is on its way. Check your inbox and spam folder.", email)
}

/** Facts about the signed-in person that the Profile screen shows and can change. */
internal class AccountViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()
    private val repository = FirebaseRepository()

    var email by mutableStateOf(auth.currentUser?.email ?: "")
        private set
    var displayName by mutableStateOf(
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").takeIf { it.isNotBlank() }
            ?: tr("MindTrack member")
    )
        private set
    var emailVerified by mutableStateOf(auth.currentUser?.isEmailVerified ?: true)
        private set

    /** True for email-and-password accounts; Google accounts confirm with Google instead. */
    val usesPassword: Boolean =
        auth.currentUser?.providerData?.any { it.providerId == com.google.firebase.auth.EmailAuthProvider.PROVIDER_ID } ?: true

    /** Re-checks that it's really the owner, using Google. Needed before an account can be deleted. */
    suspend fun reauthenticateWithGoogle(activity: android.app.Activity) {
        val token = requestGoogleIdToken(activity, filterAuthorized = true)
        auth.currentUser?.reauthenticate(com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null))?.await()
    }

    /** Returns a short status message for the person. */
    suspend fun sendVerification(): String = try {
        repository.sendVerificationEmail()
        tr("Verification email sent.")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        tr("Couldn't send the email. Please try again later.")
    }

    /** Returns null once verified, otherwise a message explaining what to do. */
    suspend fun checkVerified(): String? = try {
        emailVerified = repository.refreshVerified()
        if (emailVerified) null else tr("Not verified yet. Open the link in the email first.")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        tr("Couldn't check right now. Please try again.")
    }

    /** Returns an error message, or null when the name was saved. */
    suspend fun updateDisplayName(name: String): String? = try {
        val clean = name.trim().take(100)
        require(clean.isNotBlank()) { tr("Please enter a name.") }
        val user = auth.currentUser ?: throw IllegalStateException(tr("You are not signed in."))
        kotlinx.coroutines.withTimeout(15_000) {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
            repository.saveProfile(clean)
        }
        displayName = clean
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is kotlinx.coroutines.TimeoutCancellationException) tr("That took too long. Check your connection and try again.") else throw e
    } catch (e: Exception) {
        e.localizedMessage ?: tr("Couldn't update your name.")
    }
}

internal fun buildSuggestionPrompt(records: List<CheckInRecord>, summary: String): String {
    val contextSummary = records.sortedBy { it.date }.joinToString("\n") { record ->
        val moodName = moodOptions.firstOrNull { it.score == record.mood }?.label ?: "Unknown"
        "Date: ${record.date}; mood: $moodName (${record.mood}/5); stress: ${record.stress}/5; energy: ${record.energy}/5"
    }
    return """You are MindTrack's supportive wellbeing companion. Based only on these structured check-in records, give 3 to 5 concise, practical, gentle suggestions that directly connect to the patterns shown. If there is only one check-in, offer low-pressure suggestions appropriate to a single snapshot and do not claim a trend. Do not diagnose, imply causation, or invent facts. Avoid generic filler. Return only a numbered list, one suggestion per line, with no introduction.

Dashboard summary: $summary
Check-in records:
$contextSummary"""
}

/** The AI suggestions on the Insights screen: at most one request per day per set of check-ins. */
internal class SuggestionsViewModel(app: Application, uid: String?) : AndroidViewModel(app) {

    private val prefs = userPrefs(app, uid)
    private val quota = AiQuota.forContext(app)

    var text by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(records: List<CheckInRecord>, summary: String) {
        if (records.isEmpty() || isLoading) return
        error = null

        val today = java.time.LocalDate.now().toString()
        val signature = SuggestionCache.signature(records)
        SuggestionCache.get(prefs, today, signature)?.let {
            text = it
            return
        }

        text = ""
        isLoading = true
        viewModelScope.launch {
            try {
                GeminiCompanion(quota).sendMessageStream(buildSuggestionPrompt(records, summary)) { chunk ->
                    text = if (chunk.startsWith("\u0000")) chunk.removePrefix("\u0000") else text + chunk
                }
                if (text.isBlank()) error = tr("I couldn't create suggestions just now. Please try again.")
                else SuggestionCache.put(prefs, today, signature, text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MindTrackInsights", "AI suggestions failed", e)
                error = friendlyAiError(e)
            } finally {
                isLoading = false
            }
        }
    }
}
