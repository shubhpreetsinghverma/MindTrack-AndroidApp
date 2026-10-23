package com.mindtrack.app

import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    @After fun resetLanguage() { Lang.current = AppLanguage.ENGLISH }

    @Test fun greetingUsesTheFirstNameOnly() {
        val g = greetingFor("Shubh", hour = 10, softTone = false, random = Random(1))
        assertTrue(g.title, g.title.contains("Shubh"))
        assertFalse(g.title.contains("%"))
    }

    @Test fun noNameStillGivesAFriendlyGreeting() {
        (0..23).forEach { hour ->
            val g = greetingFor(null, hour, softTone = false, random = Random(hour))
            assertFalse(g.title.contains("%"))
            assertTrue(g.title.isNotBlank() && g.line.isNotBlank())
        }
    }

    @Test fun firstNameIgnoresPlaceholderAndBlankNames() {
        assertEquals("Shubh", firstNameOf("Shubh Singh Verma"))
        assertNull(firstNameOf("MindTrack member"))
        assertNull(firstNameOf(""))
        assertNull(firstNameOf(null))
    }

    @Test fun linesChangeWithTheTimeOfDay() {
        val night = (0..30).map { greetingFor("A", 2, false, Random(it)).line }.toSet()
        val morning = (0..30).map { greetingFor("A", 8, false, Random(it)).line }.toSet()
        assertTrue(night.intersect(morning).isEmpty())
        assertTrue("lines should vary between visits", night.size > 1)
    }

    @Test fun lowOrRoughDaysNeverGetTheJokingLines() {
        val playful = setOf("Night owl, huh?", "A sip of water and a stretch? Just saying.", "Rise and shine. Or just rise, that counts too.")
        (0..23).forEach { hour ->
            (0..20).forEach { seed ->
                assertFalse(greetingFor("A", hour, softTone = true, random = Random(seed)).line in playful)
            }
        }
    }

    @Test fun remindersVaryAndEveryOneIsTranslated() {
        assertTrue((0..40).map { gentleReminders.random(Random(it)) }.toSet().size > 3)
        Lang.current = AppLanguage.HINDI
        gentleReminders.forEach { assertNotEquals("No Hindi for: $it", it, tr(it)) }
    }

    @Test fun everyGreetingTitleAndLineHasHindi() {
        Lang.current = AppLanguage.HINDI
        (0..23).forEach { hour ->
            (0..40).forEach { seed ->
                listOf(true, false).forEach { soft ->
                    val named = greetingFor("शुभ", hour, soft, Random(seed))
                    val plain = greetingFor(null, hour, soft, Random(seed))
                    listOf(named.title, named.line, plain.title, plain.line).forEach { text ->
                        assertFalse("English left over: $text", text.any { it in 'a'..'z' })
                    }
                }
            }
        }
    }
}
