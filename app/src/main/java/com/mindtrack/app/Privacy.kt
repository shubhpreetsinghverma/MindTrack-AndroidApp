package com.mindtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

internal const val POLICY_VERSION = 2

internal data class PolicySection(val title: String, val body: String)

internal val privacyPolicy = listOf(
    PolicySection(
        "What MindTrack collects",
        "• Your account: email address and name, from email sign-up or from your Google account if you sign in with Google.\n" +
            "• Your check-ins: mood, stress and energy ratings (1 to 5), the date, tags you pick, and any journal note you write.\n" +
            "• Your companion chats: messages you send to the companion and its replies.\n" +
            "• Crash reports: device model, Android version, app version and technical error details when the app fails. They never include your notes or messages.\n" +
            "• Settings kept on your device: reminder times, theme, language, app lock and intro preferences."
    ),
    PolicySection(
        "How it is used",
        "Your check-ins are used to show your history, streaks, weekly summary and insights. " +
            "Crash reports are used only to find and fix bugs. Nothing is used for advertising and nothing is sold."
    ),
    PolicySection(
        "Where it is stored",
        "Check-ins are saved on your device and synced to your private account in Google Firebase (Authentication and Cloud Firestore). " +
            "Access rules allow only you to read or change your own data. " +
            "Companion chat history and companion memory stay on your device only and are deleted automatically after 7 days."
    ),
    PolicySection(
        "AI companion and suggestions",
        "Messages you type in the companion are sent to Google's Gemini model to produce a reply. " +
            "To make replies relevant, your recent mood, stress and energy ratings and tags are included as context. " +
            "Your journal notes are not sent to the model.\n\n" +
            "This app currently uses the free tier of the Gemini API. Under Google's terms for the free tier, content sent to the model may be used by Google to improve its products. " +
            "Please do not share anything in the companion that you would not want handled that way."
    ),
    PolicySection(
        "Not medical advice",
        "MindTrack is a wellbeing tool. It is not a diagnosis, treatment or emergency service, and it cannot contact anyone for you. " +
            "If you may be in danger, contact your local emergency number or one of the helplines in the Resource centre."
    ),
    PolicySection(
        "Your choices",
        "• Edit or delete any entry from History.\n" +
            "• Export your check-ins as a PDF from History.\n" +
            "• Clear the companion's chat and memory at any time with New chat or from Profile.\n" +
            "• Turn crash reports, the daily reminders and the app lock on or off in Profile.\n" +
            "• Delete your account and all cloud data from Profile at any time. This cannot be undone."
    ),
    PolicySection(
        "Children",
        "MindTrack is not designed for children under 13, and we do not knowingly collect their data."
    ),
    PolicySection(
        "Changes",
        "If this policy changes in a meaningful way, the app will ask you to review it again."
    )
)

private fun consentKey(uid: String?) = "consent_v${POLICY_VERSION}_${uid ?: "anon"}"

internal fun hasConsented(prefs: android.content.SharedPreferences, uid: String?) =
    prefs.getBoolean(consentKey(uid), false)

@Composable
internal fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text(tr("←  Back"), color = Lavender) }
            ScreenHeader(
                eyebrow = tr("YOUR PRIVACY"),
                title = tr("Privacy policy"),
                subtitle = tr("Plain-language summary of how MindTrack handles your data.")
            )
        }
        items(if (Lang.current == AppLanguage.HINDI) privacyPolicyHindi else privacyPolicy) { section ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceColor).padding(18.dp)
            ) {
                Text(section.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(section.body, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** First-run consent. Shown once per account (and again if the policy version changes). */
@Composable
internal fun ConsentGate(uid: String?, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { settingsPrefs(context) }
    var consented by remember(uid) { mutableStateOf(hasConsented(prefs, uid)) }
    var showPolicy by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    when {
        consented -> content()
        showPolicy -> Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
            PrivacyPolicyScreen(onBack = { showPolicy = false })
        }
        else -> Column(
            Modifier
                .fillMaxSize()
                .background(Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(tr("BEFORE YOU BEGIN"), color = Lavender, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(tr("Your reflections are yours."), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                tr("MindTrack saves your check-ins to your private account so they follow you across devices. Messages to the companion, with your recent ratings (not your journal notes), are processed by Google's Gemini AI. You can export or delete everything at any time."),
                color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp
            )
            TextButton(onClick = { showPolicy = true }) { Text(tr("Read the full privacy policy"), color = Lavender) }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = Lavender, checkmarkColor = Background)
                )
                Spacer(Modifier.width(8.dp))
                Text(tr("I understand and agree to the privacy policy."), color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    prefs.edit { putBoolean(consentKey(uid), true) }
                    CrashReporting.apply(context, uid)
                    consented = true
                },
                enabled = checked,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(tr("Continue"), fontWeight = FontWeight.Bold) }
        }
    }
}
