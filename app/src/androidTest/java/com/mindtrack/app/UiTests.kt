package com.mindtrack.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Screen tests that run on a device or emulator. They draw real screens with fake data and never touch
 * the network, the signed-in account or the person's saved check-ins.
 */
class UiTests {
    @get:Rule val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val testUid = "ui-test-user"

    @Before fun setUp() {
        Lang.current = AppLanguage.ENGLISH
        ThemeState.mode = ThemeMode.DARK
        ThemeState.systemDark = true
    }

    @After fun tearDown() {
        Lang.current = AppLanguage.ENGLISH
        ThemeState.mode = ThemeMode.SYSTEM
        ChatStore.forUser(app, testUid).clear()
        settingsPrefs(app).edit { remove("consent_v${POLICY_VERSION}_$testUid") }
        userPrefs(app, testUid).edit { clear() }
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { MaterialTheme(colorScheme = mindTrackColorScheme()) { content() } }
    }

    private fun record(date: LocalDate, mood: Int = 4, note: String = "") =
        CheckInRecord(date.toString(), mood, 2, 3, note)

    // ---- Mood screen ------------------------------------------------------------------------

    @Test fun moodScreen_choosingAMoodAndATagReportsTheChoice() {
        var chosenMood = -1
        var toggledTag = ""
        show {
            MoodScreen(
                mood = 3, stress = 3, energy = 3, journalState = mutableStateOf(""), tags = emptyList(),
                checkedIn = false, isSyncing = false, syncMessage = null, syncSuccess = false,
                onMoodChange = { chosenMood = it }, onStressChange = {}, onEnergyChange = {},
                onToggleTag = { toggledTag = it }, onSave = {}
            )
        }
        rule.onNodeWithText("Great").performClick()
        assertEquals(5, chosenMood)

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Work or study"))
        rule.onNodeWithText("Work or study").performClick()
        assertEquals("Work or study", toggledTag)
    }

    // ---- History ----------------------------------------------------------------------------

    @Test fun history_listShowsEntriesAndDeleteNeedsConfirmation() {
        val today = LocalDate.now()
        var deleted: String? = null
        show {
            HistoryScreen(
                records = listOf(record(today, note = "a quiet day")),
                reportName = "Tester", onBack = {}, onEdit = { _, _, _ -> }, onDelete = { deleted = it },
                onBackfill = { _, _, _, _, _, _ -> }
            )
        }
        rule.onNodeWithText("a quiet day").assertIsDisplayed()
        rule.onNodeWithText("a quiet day").performClick()
        rule.onNode(hasScrollAction() and hasText("Delete this entry").not()).let { }   // dialog is scrollable
        rule.onNodeWithText("Delete this entry").performClick()
        rule.onNodeWithText("Delete this entry?").assertIsDisplayed()
        assertEquals(null, deleted)                       // nothing is deleted until confirmed
        rule.onNodeWithText("Delete").performClick()
        assertEquals(today.toString(), deleted)
    }

    @Test fun history_calendarLetsYouAddAMissedDay() {
        val today = LocalDate.now()
        val missed = today.minusDays(1)
        assumeTrue("Yesterday must be in the month the calendar opens on", missed.month == today.month)
        var added: LocalDate? = null
        show {
            HistoryScreen(
                records = listOf(record(today)),
                reportName = "Tester", onBack = {}, onEdit = { _, _, _ -> }, onDelete = {},
                onBackfill = { date, _, _, _, _, _ -> added = date }
            )
        }
        rule.onNodeWithText("Calendar").performClick()
        rule.onNodeWithText("${missed.dayOfMonth}").performClick()
        rule.onNodeWithText("How were you feeling that day?").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        assertEquals(missed, added)
    }

    @Test fun history_calendarDoesNotOfferDaysBeyondThirtyDaysBack() {
        val today = LocalDate.now()
        val tooOld = today.minusDays(BACKFILL_DAYS + 1L)
        assumeTrue(tooOld.month == today.month)
        show {
            HistoryScreen(
                records = listOf(record(today)),
                reportName = "Tester", onBack = {}, onEdit = { _, _, _ -> }, onDelete = {},
                onBackfill = { _, _, _, _, _, _ -> }
            )
        }
        rule.onNodeWithText("Calendar").performClick()
        rule.onNodeWithText("${tooOld.dayOfMonth}").performClick()
        rule.onAllNodesWithTextCount("How were you feeling that day?", 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        assertEquals(expected, onAllNodes(hasText(text)).fetchSemanticsNodes().size)
    }

    // ---- Exercises --------------------------------------------------------------------------

    @Test fun breathing_startsAndMovesThroughThePhases() {
        // Voice off here so the test measures the timing only; the person's own choice is put back afterwards.
        val before = voiceGuidanceOn(app)
        setVoiceGuidance(app, false)
        try { breathingRun() } finally { setVoiceGuidance(app, before) }
    }

    @Test fun exercises_offerAVoiceGuidanceSwitch() {
        show { ExercisesScreen(onBack = {}) }
        rule.onNodeWithText("Voice guidance", substring = true).assertIsDisplayed()
    }

    private fun breathingRun() {
        show { ExercisesScreen(onBack = {}) }
        rule.onNodeWithText("Box breathing").performClick()
        rule.onNodeWithText("Ready?").assertIsDisplayed()
        // Drive the animation clock by hand so the timing is exact.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Start").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Breathe in").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(4_000)
        rule.onNodeWithText("Hold").assertIsDisplayed()
        rule.onNodeWithText("Pause").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test fun grounding_walksThroughFiveSteps() {
        show { ExercisesScreen(onBack = {}) }
        rule.onNodeWithText("5-4-3-2-1 grounding").performClick()
        rule.onNodeWithText("Step 1 of 5").assertIsDisplayed()
        repeat(4) { rule.onNodeWithText("Next").performClick() }
        rule.onNodeWithText("Step 5 of 5").assertIsDisplayed()
        rule.onNodeWithText("Finish").assertIsDisplayed()
    }

    // ---- Companion --------------------------------------------------------------------------

    @Test fun companion_simpleGreetingsAreAnsweredWithoutTheNetwork() {
        val vm = CompanionViewModel(app, testUid)
        show { CompanionScreen(vm = vm, records = emptyList(), streak = 0) }
        rule.onNodeWithText("Your companion").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("hi")
        rule.onNodeWithText("Send message  →").performClick()
        rule.waitForIdle()
        assertEquals(3, vm.messages.size)                 // welcome, "hi", local reply
        assertTrue(vm.messages.last().text.isNotBlank() && !vm.messages.last().isUser)
        assertEquals(null, vm.errorMessage)
    }

    @Test fun companion_newChatClearsTheConversation() {
        val vm = CompanionViewModel(app, testUid)
        show { CompanionScreen(vm = vm, records = emptyList(), streak = 0) }
        rule.onNode(hasSetTextAction()).performTextInput("thanks")
        rule.onNodeWithText("Send message  →").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("New chat").performClick()
        rule.waitForIdle()
        assertEquals(1, vm.messages.size)
    }

    // ---- Language, theme, navigation, consent -------------------------------------------------

    @Test fun hindi_bottomNavigationIsTranslated() {
        Lang.current = AppLanguage.HINDI
        show { BottomNavigationBar(selectedTab = AppTab.HOME, onTabSelected = {}) }
        listOf("होम", "मूड", "इनसाइट्स", "साथी", "प्रोफ़ाइल").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun bottomNavigation_reportsTheTappedTab() {
        var picked: AppTab? = null
        show { BottomNavigationBar(selectedTab = AppTab.HOME, onTabSelected = { picked = it }) }
        rule.onNodeWithText("Insights").performClick()
        assertEquals(AppTab.INSIGHTS, picked)
    }

    @Test fun insights_withNoHistoryExplainsWhatIsNeeded() {
        val suggestions = SuggestionsViewModel(app, testUid)
        show {
            InsightsScreen(
                records = emptyList(), checkedInToday = false, weekly = null,
                suggestions = suggestions, allRecords = emptyList(), dismissedInsights = emptySet(), onDismissInsight = {}
            )
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Patterns worth noticing"))
        rule.onNodeWithText("Patterns worth noticing").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Mood outlook"))
        rule.onNodeWithText("EXPERIMENTAL").assertIsDisplayed()
    }

    @Test fun consent_continueStaysDisabledUntilTheBoxIsTicked() {
        show { ConsentGate(uid = testUid) { androidx.compose.material3.Text("APP CONTENT") } }
        rule.onNodeWithText("Your reflections are yours.").assertIsDisplayed()
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("I understand and agree to the privacy policy.").performClick()
        rule.onNodeWithText("Continue").assertIsEnabled()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("APP CONTENT").assertIsDisplayed()
    }

    @Test fun lockScreen_coversTheAppWhileLocked() {
        AppLock.enabled = true
        AppLock.locked = true
        try {
            show { LockGate { androidx.compose.material3.Text("SECRET") } }
            rule.onNodeWithText("Locked to keep your reflections private.").assertIsDisplayed()
            rule.onNodeWithText("Unlock").assertIsDisplayed()
        } finally {
            AppLock.enabled = false
            AppLock.locked = false
        }
    }
}
