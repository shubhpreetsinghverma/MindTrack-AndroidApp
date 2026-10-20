package com.mindtrack.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class Helpline(val region: String, val name: String, val dial: String?, val display: String, val url: String?)

internal val helplines = listOf(
    Helpline("INDIA", "Tele-MANAS (24/7)", "14416", "14416", "https://telemanas.mohfw.gov.in/"),
    Helpline("INDIA · EMERGENCY", "National emergency number", "112", "112", null),
    Helpline("US & CANADA", "988 Suicide & Crisis Lifeline", "988", "988 (call or text)", "https://988lifeline.org/"),
    Helpline("UK & IRELAND", "Samaritans (24/7)", "116123", "116 123", "https://www.samaritans.org/"),
    Helpline("ANYWHERE ELSE", "Find a helpline in your country", null, "findahelpline.com", "https://findahelpline.com/")
)

/**
 * A fixed, tested safety net. It does not depend on what the AI model says: if a
 * message or journal note contains clear signs of crisis, the app always shows
 * real-world help.
 */
internal object CrisisSupport {
    private val phrases: List<String> = listOf(
        "kill myself", "killing myself", "end my life", "ending my life", "take my own life",
        "want to die", "wanna die", "wish i was dead", "wish i were dead", "better off dead",
        "suicide", "suicidal", "self harm", "self-harm", "selfharm", "hurt myself", "harm myself",
        "cut myself", "cutting myself", "don't want to live", "dont want to live",
        "do not want to live", "no reason to live", "not want to be alive", "end it all",
        "overdose on", "can't go on", "cant go on", "no point in living"
    ) + hindiCrisisPhrases

    fun needsSupport(text: String): Boolean {
        val normalised = text.lowercase().replace('’', '\'')
        return phrases.any { it in normalised }
    }

    const val CHAT_MESSAGE_EN =
        "I'm really glad you told me, and I'm concerned about how much pain you may be in. " +
            "You don't have to carry this alone. If you might act on these thoughts, or you are in immediate danger, " +
            "please contact your local emergency number now (112 in India, 911 in the US, 999 in the UK).\n\n" +
            "You can also talk to someone right now:\n" +
            "• India: Tele-MANAS 14416\n" +
            "• US & Canada: call or text 988\n" +
            "• UK & Ireland: Samaritans 116 123\n" +
            "• Elsewhere: findahelpline.com\n\n" +
            "If there's someone you trust nearby, please consider telling them how you feel. I'm still here and happy to keep talking."

    /** Shown in the companion chat when crisis wording is detected. */
    val chatMessage: String get() = tr(CHAT_MESSAGE_EN)
}

internal fun openDialer(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

internal fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
internal fun HelplineList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        helplines.forEachIndexed { index, line ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .clickable {
                        when {
                            line.dial != null -> openDialer(context, line.dial)
                            line.url != null -> openLink(context, line.url)
                        }
                    }
                    .padding(14.dp)
            ) {
                Text(tr(line.region), color = Lavender, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(tr(line.name), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (line.dial != null) trf("Tap to call  ·  %1\$s", tr(line.display)) else trf("Tap to open  ·  %1\$s", tr(line.display)),
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun SupportDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text(tr("You're not alone"), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    tr("What you wrote sounds really heavy. If you're in immediate danger, please call your local emergency number. These services are free and confidential:"),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                HelplineList()
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Background)
            ) { Text(tr("I'm okay for now")) }
        }
    )
}

/**
 * Hindi crisis wording, in Devanagari and in the Roman spelling many people type. Needs review by a
 * Hindi-speaking counsellor before wider release (see CRISIS_REVIEW.md).
 */
internal val hindiCrisisPhrases = listOf(
    "आत्महत्या", "खुदकुशी", "ख़ुदकुशी", "मरना चाहता", "मरना चाहती", "मर जाना चाहता", "मर जाना चाहती",
    "जान दे दूँ", "जान दे दूं", "जान देना चाहता", "जान देना चाहती", "खुद को खत्म", "खुद को ख़त्म", "खुद को मार",
    "जीना नहीं चाहता", "जीना नहीं चाहती", "जीने का मन नहीं", "जीने की इच्छा नहीं", "जीने का कोई मतलब नहीं",
    "खुद को नुकसान", "खुद को चोट पहुँचा", "खुद को चोट पहुंचा", "सब खत्म कर दूँ", "सब खत्म कर दूं",
    "atmahatya", "khudkushi", "marna chahta", "marna chahti", "mar jana chahta", "mar jana chahti",
    "jaan de dun", "jeena nahi chahta", "jeena nahi chahti", "jeene ka mann nahi", "khud ko khatam"
)
