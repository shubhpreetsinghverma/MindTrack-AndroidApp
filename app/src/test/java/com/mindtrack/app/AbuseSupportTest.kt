package com.mindtrack.app

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbuseSupportTest {
    @After fun resetLanguage() { Lang.current = AppLanguage.ENGLISH }

    @Test fun clearDisclosuresAreFlagged() {
        listOf(
            "I was sexually assaulted last night", "he raped me", "my uncle molested me when I was young",
            "my boyfriend keeps hitting me", "I'm being abused at home", "someone is blackmailing me with my photos",
            "I am afraid of my husband", "it's not safe at home", "she beat me again", "domestic violence is happening to me",
            "मेरे साथ बलात्कार हुआ", "वो मुझे मारता है", "मेरे साथ छेड़छाड़ हुई", "घरेलू हिंसा हो रही है"
        ).forEach { assertTrue("should flag: $it", CrisisSupport.needsAbuseSupport(it)) }
    }

    @Test fun ordinaryTalkIsNotFlagged() {
        listOf(
            "I ate a grape and scraped my knee", "the draper beat the deadline", "I need to hit my study goal",
            "exams are abusing my sleep schedule", "he hit a six in cricket", "I hit snooze again",
            "I'm afraid of exams", "today I felt tired", "परीक्षा का तनाव है", "आज मैं बहुत थका हुआ हूँ"
        ).forEach { assertFalse("should not flag: $it", CrisisSupport.needsAbuseSupport(it)) }
    }

    @Test fun selfHarmAndAbuseAreSeparateChecksButBothBringHelp() {
        assertTrue(CrisisSupport.needsAnySupport("I want to die"))
        assertTrue(CrisisSupport.needsAnySupport("he raped me"))
        assertFalse(CrisisSupport.needsAbuseSupport("I want to die"))
        assertFalse(CrisisSupport.needsAnySupport("having a good day"))
    }

    @Test fun theAbuseMessageNamesRealHelplinesInBothLanguages() {
        val en = CrisisSupport.abuseChatMessage
        assertTrue(en.contains("181") && en.contains("1098") && en.contains("112") && en.contains("not your fault"))
        Lang.current = AppLanguage.HINDI
        val hi = CrisisSupport.abuseChatMessage
        assertTrue(hi.contains("181") && hi.contains("1098"))
        assertTrue(hi.any { it in 'ऀ'..'ॿ' })
    }

    @Test fun anAbuseDisclosureIsNeverAnsweredWithACannedReply() {
        assertTrue(trivialReply("hi, he raped me") == null)
    }

    @Test fun theHelplineListIncludesTheNewNumbers() {
        val dials = helplines.mapNotNull { it.dial }
        listOf("181", "1098", "112", "18006564673", "08088029999").forEach { assertTrue("missing $it", it in dials) }
    }
}
