
package com.mindtrack.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Firebase operations for MindTrack.
 *
 * Firestore structure:
 * users/{uid}/checkIns/{yyyy-MM-dd}
 *
 * This class does not replace the app's existing local storage.
 */
/** What the check-in storage needs from the cloud. A fake can stand in for it in tests. */
internal interface CheckInCloud {
    suspend fun saveCheckIn(date: String, mood: Int, stress: Int, energy: Int, journal: String, tags: List<String>)
    suspend fun deleteCheckIn(date: String)
    suspend fun loadCheckIns(limit: Long): List<Map<String, Any>>
}

class FirebaseRepository : CheckInCloud {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(
        listener: (FirebaseUser?) -> Unit
    ): FirebaseAuth.AuthStateListener {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            listener(firebaseAuth.currentUser)
        }

        auth.addAuthStateListener(authListener)
        return authListener
    }

    fun removeAuthListener(
        listener: FirebaseAuth.AuthStateListener
    ) {
        auth.removeAuthStateListener(listener)
    }

    suspend fun register(
        email: String,
        password: String
    ): FirebaseUser {
        require(email.isNotBlank()) {
            "Please enter your email address."
        }
        require(password.length >= 6) {
            "Your password must contain at least 6 characters."
        }

        return auth.createUserWithEmailAndPassword(
            email.trim(),
            password
        ).await().user
            ?: throw IllegalStateException(
                "Account creation succeeded, but no user was returned."
            )
    }

    suspend fun signIn(
        email: String,
        password: String
    ): FirebaseUser {
        require(email.isNotBlank()) {
            "Please enter your email address."
        }
        require(password.isNotBlank()) {
            "Please enter your password."
        }

        return auth.signInWithEmailAndPassword(
            email.trim(),
            password
        ).await().user
            ?: throw IllegalStateException(
                "Sign-in succeeded, but no user was returned."
            )
    }

    fun signOut() {
        auth.signOut()
    }

    override suspend fun saveCheckIn(
        date: String,
        mood: Int,
        stress: Int,
        energy: Int,
        journal: String,
        tags: List<String>
    ) {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException(
                "Sign in before saving a cloud check-in."
            )

        require(mood in 1..5) { "Mood must be between 1 and 5." }
        require(stress in 1..5) { "Stress must be between 1 and 5." }
        require(energy in 1..5) { "Energy must be between 1 and 5." }

        val data = mapOf(
            "date" to date,
            "mood" to mood,
            "stress" to stress,
            "energy" to energy,
            "journal" to journal.trim().take(2000),
            "tags" to tags.take(MAX_TAGS),
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("checkIns")
            .document(date)
            .set(data, SetOptions.merge())
            .await()
    }

    override suspend fun deleteCheckIn(date: String) {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in before deleting a cloud check-in.")
        firestore.collection("users").document(userId)
            .collection("checkIns").document(date)
            .delete().await()
    }

    override suspend fun loadCheckIns(
        limit: Long
    ): List<Map<String, Any>> {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException(
                "Sign in before loading cloud check-ins."
            )

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("checkIns")
            .orderBy("date")
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.data?.toMutableMap()?.apply {
                put("date", get("date") ?: document.id)
            }
        }
    }

    suspend fun saveProfile(
        displayName: String
    ) {
        val user = auth.currentUser
            ?: throw IllegalStateException(
                "Sign in before saving your profile."
            )

        val data = mapOf(
            "displayName" to displayName.trim().take(100),
            "email" to (user.email ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    /** Re-checks the password; Firebase requires a recent sign-in before an account can be deleted. */
    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("You are not signed in.")
        val email = user.email
            ?: throw IllegalStateException("This account has no email address.")
        user.reauthenticate(
            com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        ).await()
    }

    /** Deletes every check-in and the profile document. */
    suspend fun deleteCloudData() {
        val user = auth.currentUser
            ?: throw IllegalStateException("You are not signed in.")
        val userDoc = firestore.collection("users").document(user.uid)
        val checkIns = userDoc.collection("checkIns")

        while (true) {
            val batchDocs = checkIns.limit(400).get().await().documents
            if (batchDocs.isEmpty()) break
            val batch = firestore.batch()
            batchDocs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        userDoc.delete().await()
    }

    /** Deletes the sign-in account itself. Call last. */
    suspend fun deleteAuthUser() {
        auth.currentUser?.delete()?.await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun sendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Refreshes the cached user and returns whether the email is now verified. */
    suspend fun refreshVerified(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        return user.isEmailVerified
    }
}
