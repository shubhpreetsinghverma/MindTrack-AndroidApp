package com.mindtrack.app

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Google sign-in failed or was not possible; [message] is safe to show to the person. */
internal class GoogleSignInProblem(override val message: String, val cancelled: Boolean = false) : Exception(message)

/**
 * Asks Android's Credential Manager for a Google ID token.
 *
 * It needs the web client ID that Firebase generates once Google is switched on as a sign-in method
 * (it appears as the `default_web_client_id` string). Until then the person gets a clear message.
 */
internal suspend fun requestGoogleIdToken(activity: Activity, filterAuthorized: Boolean = false): String {
    val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    val webClientId = if (resId != 0) activity.getString(resId) else ""
    if (webClientId.isBlank()) {
        throw GoogleSignInProblem(tr("Google sign-in isn't set up for this build yet."))
    }

    val option = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(filterAuthorized)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val credential = try {
        CredentialManager.create(activity).getCredential(activity, request).credential
    } catch (e: GetCredentialCancellationException) {
        throw GoogleSignInProblem(tr("Google sign-in was cancelled."), cancelled = true)
    } catch (e: NoCredentialException) {
        throw GoogleSignInProblem(tr("No Google account was found on this device."))
    } catch (e: GetCredentialException) {
        throw GoogleSignInProblem(tr("Couldn't sign in with Google. Please try again."))
    }

    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw GoogleSignInProblem(tr("Couldn't sign in with Google. Please try again."))
}
