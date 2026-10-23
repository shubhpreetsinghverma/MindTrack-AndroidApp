package com.mindtrack.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class MainActivity : FragmentActivity() {
    private var quickMood by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(context = this)
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Firebase.appCheck.installAppCheckProviderFactory(
            if (debuggable) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        readQuickMood(intent)

        val settings = settingsPrefs(this)
        ThemeState.systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeState.mode = readThemeMode(settings)
        Lang.current = readLanguage(settings)
        CrashReporting.apply(this)
        AppLock.init(settings)
        if (AppLock.enabled) applySecureFlag(this, true)
        lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> AppLock.onStopped()
                androidx.lifecycle.Lifecycle.Event.ON_START -> AppLock.onStarted()
                else -> Unit
            }
        })
        val firstLaunch = !settings.getBoolean("intro_seen", false)

        setContent {
            MaterialTheme(colorScheme = mindTrackColorScheme(), shapes = mindTrackShapes) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    var showBrandSplash by remember { mutableStateOf(true) }

                    // The intro is always dark, so the status bar icons stay light until the app appears.
                    val darkBars = showBrandSplash || ThemeState.isDark
                    androidx.compose.runtime.SideEffect {
                        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !darkBars
                        controller.isAppearanceLightNavigationBars = !darkBars
                    }

                    Crossfade(
                        targetState = showBrandSplash,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "mindTrackSplashToApp"
                    ) { showingSplash ->
                        if (showingSplash) {
                            MindTrackAnimatedSplash(
                                firstLaunch = firstLaunch,
                                onFinished = {
                                    settings.edit { putBoolean("intro_seen", true) }
                                    showBrandSplash = false
                                }
                            )
                        } else {
                            FirebaseAuthGate { uid ->
                                LockGate {
                                    ConsentGate(uid = uid) {
                                        MindTrackApp(
                                            uid = uid,
                                            quickMood = quickMood,
                                            onQuickMoodConsumed = { quickMood = null }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readQuickMood(intent)
    }

    private fun readQuickMood(intent: Intent?) {
        val score = intent?.getIntExtra(EXTRA_QUICK_MOOD, 0) ?: 0
        if (score in 1..5) quickMood = score
        intent?.removeExtra(EXTRA_QUICK_MOOD)
    }
}
