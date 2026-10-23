package com.mindtrack.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity

/** After this long in the background the app asks for the fingerprint or screen lock again. */
internal const val LOCK_TIMEOUT_MS = 60_000L
private const val KEY_APP_LOCK = "app_lock"

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal fun shouldLockAfterBackground(enabled: Boolean, backgroundedAt: Long, now: Long, timeoutMs: Long = LOCK_TIMEOUT_MS) =
    enabled && backgroundedAt > 0 && now - backgroundedAt >= timeoutMs

/** Holds whether the lock is switched on and whether the app is locked right now. */
internal object AppLock {
    var enabled by mutableStateOf(false)
    var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    /** Called at launch: a cold start always begins locked when the lock is on. */
    fun init(prefs: android.content.SharedPreferences) {
        enabled = prefs.getBoolean(KEY_APP_LOCK, false)
        locked = enabled
    }

    fun onStopped(now: Long = System.currentTimeMillis()) { backgroundedAt = now }

    fun onStarted(now: Long = System.currentTimeMillis()) {
        if (shouldLockAfterBackground(enabled, backgroundedAt, now)) locked = true
    }

    fun setEnabled(prefs: android.content.SharedPreferences, on: Boolean) {
        enabled = on
        locked = false
        prefs.edit { putBoolean(KEY_APP_LOCK, on) }
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

internal fun canUseAppLock(context: Context) =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

private fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    )
}

/** Keeps the screen hidden from screenshots and the recent-apps view while the lock is on. */
internal fun applySecureFlag(activity: Activity, secure: Boolean) {
    if (secure) activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

/** Covers the app with a lock screen while [AppLock.locked]; the app underneath keeps its place. */
@Composable
internal fun LockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    Box(Modifier.fillMaxSize()) {
        content()
        if (AppLock.enabled && AppLock.locked) {
            fun unlock() {
                activity?.let { authenticate(it, tr("Unlock MindTrack")) { AppLock.locked = false } }
            }
            LaunchedEffect(Unit) { unlock() }

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("✳", color = Lavender, fontSize = 40.sp)
                Spacer(Modifier.height(16.dp))
                Text("MindTrack", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(tr("Locked to keep your reflections private."), color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { unlock() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
                ) { Text(tr("Unlock"), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun AppLockCard() {
    val context = LocalContext.current
    val prefs = remember { settingsPrefs(context) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().flatSection()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(tr("App lock"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Ask for your fingerprint, face or screen lock when you open MindTrack or return after a minute away."),
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
            Switch(
                checked = AppLock.enabled,
                onCheckedChange = { on ->
                    val activity = context.findActivity()
                    if (!on) {
                        AppLock.setEnabled(prefs, false)
                        activity?.let { applySecureFlag(it, false) }
                        notice = null
                    } else if (!canUseAppLock(context)) {
                        notice = tr("Set up a screen lock or fingerprint in your phone's settings first.")
                    } else if (activity is FragmentActivity) {
                        // Confirm it's really you before the lock is turned on.
                        authenticate(activity, tr("Confirm to turn on app lock")) {
                            AppLock.setEnabled(prefs, true)
                            applySecureFlag(activity, true)
                            notice = null
                        }
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Background)
            )
        }
        notice?.let { Text(it, color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}
