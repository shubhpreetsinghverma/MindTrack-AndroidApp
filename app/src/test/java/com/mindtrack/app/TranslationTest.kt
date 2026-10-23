package com.mindtrack.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val placeholder = Regex("""%\d+\$[sd]""")

/** Kotlin-escaped text as written in source → the real string. */
private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\$", "$").replace("\\\\", "\\")

private fun sourceKeys(): Set<String> {
    val dir = File("src/main/java/com/mindtrack/app")
    val keys = mutableSetOf<String>()
    dir.listFiles { f -> f.extension == "kt" && f.name !in setOf("Strings.kt", "TranslationsHindi.kt", "TranslationsHindiLong.kt") }!!
        .forEach { file ->
            Regex("""\btrf?\(\s*"((?:[^"\\]|\\.)*)"""").findAll(file.readText()).forEach { keys += unescape(it.groupValues[1]) }
        }
    return keys
}

class TranslationTest {
    @After fun resetLanguage() { Lang.current = AppLanguage.ENGLISH }

    @Test fun englishReturnsTheTextUnchanged() {
        Lang.current = AppLanguage.ENGLISH
        assertEquals("Sign in", tr("Sign in"))
    }

    @Test fun hindiTranslatesAndFallsBackToEnglishWhenMissing() {
        Lang.current = AppLanguage.HINDI
        assertEquals("साइन इन", tr("Sign in"))
        assertEquals("a string with no translation", tr("a string with no translation"))
    }

    @Test fun everyTranslatedStringInTheCodeHasAHindiVersion() {
        val translated = hindiAll.keys.map { unescape(it) }.toSet()
        val skip = setOf("MindTrack", "✳ MindTrack", "")
        val missing = sourceKeys().filter { it !in translated && it !in skip && it.any(Char::isLetter) }
        assertTrue("Missing Hindi for: ${missing.take(15)}", missing.isEmpty())
    }

    @Test fun hindiKeepsTheSamePlaceholdersAsEnglish() {
        hindiAll.forEach { (english, hindi) ->
            val e = placeholder.findAll(unescape(english)).map { it.value }.toSet()
            val h = placeholder.findAll(hindi).map { it.value }.toSet()
            assertEquals("Placeholders differ for: $english", e, h)
        }
    }

    @Test fun formattingWorksInHindi() {
        Lang.current = AppLanguage.HINDI
        assertEquals("लगातार 5 दिन", trf("%1\$d days in a row", 5))
    }

    @Test fun hindiCrisisWordingIsDetectedWithoutTriggeringOnOrdinaryHindi() {
        assertTrue(CrisisSupport.needsSupport("मैं मरना चाहता हूँ"))
        assertTrue(CrisisSupport.needsSupport("main atmahatya ke baare mein soch raha hoon"))
        assertTrue(CrisisSupport.needsSupport("मुझे जीना नहीं चाहती अब"))
        assertFalse(CrisisSupport.needsSupport("आज मैं बहुत थका हुआ हूँ"))
        assertFalse(CrisisSupport.needsSupport("परीक्षा का तनाव है"))
    }

    @Test fun hindiGreetingsAndThanksAreAnsweredLocallyInHindi() {
        Lang.current = AppLanguage.HINDI
        val reply = trivialReply("नमस्ते")
        assertNotNull(reply)
        assertTrue(reply!!.any { it in 'ऀ'..'ॿ' })
        assertNotNull(trivialReply("धन्यवाद!"))
        assertNull(trivialReply("नमस्ते, मैं बहुत परेशान हूँ"))
    }

    @Test fun hindiPolicyCoversTheSameSectionsAsEnglish() {
        assertEquals(privacyPolicy.size, privacyPolicyHindi.size)
        assertTrue(privacyPolicyHindi.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }

    @Test fun crisisMessageHasAHindiVersionWithTheHelplines() {
        Lang.current = AppLanguage.HINDI
        val msg = CrisisSupport.chatMessage
        assertTrue(msg.contains("14416"))
        assertTrue(msg.any { it in 'ऀ'..'ॿ' })
    }
}
