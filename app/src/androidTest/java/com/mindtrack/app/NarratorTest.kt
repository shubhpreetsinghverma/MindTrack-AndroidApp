package com.mindtrack.app

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Checks the phone's own text-to-speech engine can start for each language the app speaks. */
class NarratorTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun statusFor(language: AppLanguage): VoiceStatus {
        var narrator: Narrator? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { narrator = Narrator(app, language) }
        val deadline = System.currentTimeMillis() + 20_000
        while (narrator!!.status == VoiceStatus.LOADING && System.currentTimeMillis() < deadline) Thread.sleep(200)
        val status = narrator!!.status
        Log.i("NarratorTest", "$language voice status: $status")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { narrator!!.shutdown() }
        return status
    }

    @Test fun englishVoiceStartsAndFinishesSpeaking() {
        var narrator: Narrator? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { narrator = Narrator(app, AppLanguage.ENGLISH) }
        val deadline = System.currentTimeMillis() + 20_000
        while (narrator!!.status == VoiceStatus.LOADING && System.currentTimeMillis() < deadline) Thread.sleep(200)
        Log.i("NarratorTest", "English voice status: ${narrator!!.status}")
        assertTrue("English voice should be ready, was ${narrator!!.status}", narrator!!.status == VoiceStatus.READY)

        val spoken = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { narrator!!.speakThen("Breathe in") { spoken.countDown() } }
        assertTrue("the voice never reported finishing", spoken.await(20, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { narrator!!.shutdown() }
    }

    @Test fun hindiVoiceIsReadyOrClearlyReportedAsMissing() {
        val status = statusFor(AppLanguage.HINDI)
        assertTrue("unexpected Hindi status: $status", status == VoiceStatus.READY || status == VoiceStatus.MISSING_DATA || status == VoiceStatus.UNAVAILABLE)
    }
}
