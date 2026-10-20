package com.mindtrack.app

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reports are off until the person has accepted the current privacy policy, and stay under their
 * control afterwards. Reports contain technical details only, never notes or messages.
 */
internal object CrashReporting {
    private const val KEY = "crash_reports"

    fun isOn(context: Context) = settingsPrefs(context).getBoolean(KEY, true)

    /** Applies the current choice for [uid]. Call at startup and after consent or the toggle changes. */
    fun apply(context: Context, uid: String? = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()) {
        runCatching {
            val consented = uid != null && hasConsented(settingsPrefs(context), uid)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(consented && isOn(context))
        }
    }

    fun setOn(context: Context, on: Boolean) {
        settingsPrefs(context).edit { putBoolean(KEY, on) }
        apply(context)
    }
}
