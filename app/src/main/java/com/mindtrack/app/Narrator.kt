package com.mindtrack.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import java.util.Locale

internal enum class VoiceStatus { LOADING, READY, MISSING_DATA, UNAVAILABLE }

/**
 * Reads the exercises aloud using the phone's own text-to-speech voice: an English voice in English and a
 * Hindi voice in Hindi. Nothing is sent over the network by the app, and it works offline once the voice
 * is installed on the phone. The voices themselves come from the phone (Google, Samsung and so on).
 */
internal class Narrator(context: Context, private val language: AppLanguage) {

    var status by mutableStateOf(VoiceStatus.LOADING)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var utteranceCounter = 0
    private val callbacks = mutableMapOf<String, () -> Unit>()

    init {
        engine = TextToSpeech(context.applicationContext) { code ->
            main.post { if (code == TextToSpeech.SUCCESS) configure() else status = VoiceStatus.UNAVAILABLE }
        }
    }

    private fun candidateLocales(): List<Locale> = when (language) {
        AppLanguage.ENGLISH -> listOf(Locale("en", "IN"), Locale.US, Locale.UK, Locale.ENGLISH)
        AppLanguage.HINDI -> listOf(Locale("hi", "IN"))
    }

    private fun configure() {
        val tts = engine ?: return
        var sawMissingData = false
        for (locale in candidateLocales()) {
            when (tts.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA -> sawMissingData = true
                TextToSpeech.LANG_NOT_SUPPORTED -> Unit
                else -> {
                    tts.setSpeechRate(0.9f)   // a little slower than normal speech feels calmer
                    tts.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { finish(utteranceId) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { finish(utteranceId) }
                    })
                    status = VoiceStatus.READY
                    return
                }
            }
        }
        status = if (sawMissingData) VoiceStatus.MISSING_DATA else VoiceStatus.UNAVAILABLE
    }

    private fun finish(id: String?) {
        val done = synchronized(callbacks) { callbacks.remove(id) }
        if (done != null) main.post(done)
    }

    /** Speaks [text] now, cutting off anything still being said. */
    fun speak(text: String) {
        if (status != VoiceStatus.READY) return
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${utteranceCounter++}")
    }

    /** Speaks [text] and calls [onDone] when it has finished (or failed). Calls it straight away if there is no voice. */
    fun speakThen(text: String, onDone: () -> Unit) {
        val tts = engine
        if (status != VoiceStatus.READY || tts == null) { onDone(); return }
        val id = "u${utteranceCounter++}"
        synchronized(callbacks) { callbacks[id] = onDone }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    fun stop() {
        synchronized(callbacks) { callbacks.clear() }
        engine?.stop()
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }
}

private const val KEY_VOICE = "voice_guidance"

internal fun voiceGuidanceOn(context: Context) = settingsPrefs(context).getBoolean(KEY_VOICE, true)

internal fun setVoiceGuidance(context: Context, on: Boolean) = settingsPrefs(context).edit { putBoolean(KEY_VOICE, on) }

@Composable
internal fun rememberNarrator(): Narrator {
    val context = LocalContext.current
    val language = Lang.current
    val narrator = remember(language) { Narrator(context, language) }
    DisposableEffect(narrator) { onDispose { narrator.shutdown() } }
    return narrator
}

/** Opens the phone's setting for downloading a missing voice. */
internal fun installVoiceData(context: Context) {
    runCatching {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
