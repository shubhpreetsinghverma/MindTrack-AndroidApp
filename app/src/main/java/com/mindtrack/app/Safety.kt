package com.mindtrack.app

import androidx.compose.ui.draw.drawBehind
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
    Helpline("INDIA · WOMEN", "Women Helpline (24/7)", "181", "181", null),
    Helpline("INDIA · CHILDREN", "Childline (24/7)", "1098", "1098", "https://www.childlineindia.org/"),
    Helpline("US · SEXUAL ASSAULT", "RAINN National Hotline", "18006564673", "1-800-656-4673", "https://www.rainn.org/"),
    Helpline("UK · SEXUAL ABUSE", "Rape Crisis helpline", "08088029999", "0808 802 9999", "https://rapecrisis.org.uk/"),
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

    // Abuse, assault and harassment are a different kind of emergency from self-harm, so they get their own
    // wording and helplines. Whole-word matching keeps words like "grape" and "scrape" from setting it off.
    private val abuseWords: List<Regex> = listOf(
        """\braped\b""", """\braping\b""", """\brapist\b""", """\bmolest(ed|ing|s)?\b""", """\bgroped?\b""",
        """\bsexual(ly)? (assault|abus|harass|violen)\w*""",
        """\b(physical|domestic|emotional) (abuse|violence)\b""",
        """\b(assault|abus|beat|hit)(ed|s|ing)? me\b""",
        """\b(is|was|been|being|keeps|kept) (hitting|beating|abusing|hurting|touching|molesting|threatening) me\b""",
        """\bbeing abused\b""", """\bblackmail(ed|ing)? me\b""", """\bstalk(ed|ing|s) me\b""",
        """\bafraid of my (husband|wife|partner|father|mother|boyfriend|girlfriend|boss|teacher|uncle|brother)\b""",
        """\b(not|isn't|unsafe) safe (at|in) (home|my house)\b"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val abuseHindi = listOf(
        "बलात्कार", "रेप", "यौन शोषण", "यौन उत्पीड़न", "यौन उत्पीडन", "छेड़छाड़", "छेड़खानी", "घरेलू हिंसा",
        "मारपीट", "मुझे मारता", "मुझे मारती", "मुझे पीटता", "मुझे पीटती", "मुझे छूता", "मुझे छूती", "ब्लैकमेल"
    )

    fun needsAbuseSupport(text: String): Boolean {
        val normalised = text.replace('’', '\'')
        return abuseWords.any { it.containsMatchIn(normalised) } || abuseHindi.any { it in normalised }
    }

    /** Any message that should always bring real-world help, whatever the AI says next. */
    fun needsAnySupport(text: String): Boolean = needsSupport(text) || needsAbuseSupport(text)

    const val ABUSE_CHAT_MESSAGE_EN =
        "I'm so sorry this happened to you, and I'm glad you told me. What happened is not your fault, and you deserve to be safe and supported. " +
            "If you are in danger right now, please call your local emergency number (112 in India, 911 in the US, 999 in the UK).\n\n" +
            "You can talk in confidence to someone trained to help:\n" +
            "• India: Women Helpline 181 · Childline 1098 · Police 112\n" +
            "• US: RAINN 1-800-656-4673\n" +
            "• UK: Rape Crisis 0808 802 9999\n" +
            "• Elsewhere: findahelpline.com\n\n" +
            "You decide what to share and when. I'm here, and happy to keep listening."

    val abuseChatMessage: String get() = tr(ABUSE_CHAT_MESSAGE_EN)

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
        helplines.forEach { line ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        when {
                            line.dial != null -> openDialer(context, line.dial)
                            line.url != null -> openLink(context, line.url)
                        }
                    }
                    .drawBehind { drawLine(Border, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
                    .padding(vertical = 14.dp)
            ) {
                Text(tr(line.region), color = Lavender, style = PixelLabelStyle)
                Spacer(Modifier.height(4.dp))
                Text(tr(line.name), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (line.dial != null) trf("Tap to call  ·  %1\$s", tr(line.display)) else trf("Tap to open  ·  %1\$s", tr(line.display)),
                    color = TextSecondary, fontSize = 13.sp
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
