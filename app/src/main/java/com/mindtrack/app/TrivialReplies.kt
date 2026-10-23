package com.mindtrack.app

/**
 * Hand-written replies for messages that don't need an AI at all ("hi", "thanks", "bye").
 * Answering these locally saves free-tier quota. Only a message that is *entirely* one of these
 * is answered here; anything longer, anything with a feeling in it, and anything that mentions
 * crisis wording always goes to the real companion.
 */
private val greetings = setOf(
    "hi", "hii", "hiii", "hey", "heyy", "hello", "hello there", "hi there", "hey there", "hola", "namaste",
    "good morning", "good afternoon", "good evening", "gm", "yo", "sup",
    "नमस्ते", "नमस्कार", "हेलो", "हैलो", "हाय", "सुप्रभात", "शुभ संध्या"
)
private val thanks = setOf(
    "thanks", "thank you", "thank u", "thx", "ty", "thanks a lot", "thank you so much", "thanks so much",
    "many thanks", "thanks a bunch", "shukriya", "dhanyavad",
    "धन्यवाद", "शुक्रिया", "थैंक्स", "थैंक यू", "बहुत धन्यवाद", "बहुत शुक्रिया"
)
private val goodbyes = setOf(
    "bye", "bye bye", "goodbye", "good bye", "see you", "see ya", "see you later", "cya", "ttyl",
    "good night", "gn", "night", "talk later", "talk to you later",
    "अलविदा", "बाय", "फिर मिलेंगे", "शुभ रात्रि", "गुड नाइट"
)
private val acknowledgements = setOf("ok", "okay", "k", "kk", "cool", "alright", "got it", "sure", "fine", "hmm", "hmmm", "yes", "yeah", "yep", "ठीक है", "अच्छा", "हाँ", "हां", "जी", "ठीक")

private val greetingReplies = listOf(
    "Hi, I'm glad you're here. How are you feeling today?",
    "Hello. It's good to see you. What's on your mind?",
    "Hey. I'm here. How has your day been so far?"
)
private val thanksReplies = listOf(
    "You're welcome. I'm here whenever you want to talk.",
    "Anytime. Be kind to yourself today.",
    "Glad I could help. I'm here if there's more on your mind."
)
private val goodbyeReplies = listOf(
    "Take care of yourself. I'll be here when you want to come back.",
    "Goodbye for now. Be gentle with yourself.",
    "Rest well. I'm here whenever you need a quiet moment."
)
private val acknowledgementReplies = listOf(
    "Okay. I'm here whenever you want to continue.",
    "Take your time. There's no rush."
)

private val greetingRepliesHi = listOf(
    "नमस्ते, आपका यहाँ होना अच्छा लगा। आज आप कैसा महसूस कर रहे हैं?",
    "नमस्ते। आपके मन में क्या चल रहा है?",
    "हेलो। मैं यहीं हूँ। आपका दिन अब तक कैसा रहा?"
)
private val thanksRepliesHi = listOf(
    "आपका स्वागत है। जब भी बात करनी हो, मैं यहीं हूँ।",
    "कभी भी। आज खुद के प्रति दयालु रहिएगा।",
    "खुशी हुई कि मदद कर सका। मन में कुछ और हो तो मैं यहीं हूँ।"
)
private val goodbyeRepliesHi = listOf(
    "अपना ख्याल रखिएगा। जब मन करे, मैं यहीं मिलूँगा।",
    "अभी के लिए अलविदा। खुद के प्रति नरम रहिएगा।",
    "अच्छे से आराम कीजिए। जब भी शांत पल चाहिए, मैं यहीं हूँ।"
)
private val acknowledgementRepliesHi = listOf(
    "ठीक है। जब आप आगे बात करना चाहें, मैं यहीं हूँ।",
    "आराम से, कोई जल्दी नहीं है।"
)

private fun normalise(text: String): String =
    text.lowercase()
        .replace('’', '\'')
        .replace(Regex("""[^\p{L}\p{M}\p{N}\s']"""), " ")   // punctuation and emoji
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun pick(options: List<String>, seed: String) = options[Math.floorMod(seed.hashCode(), options.size)]

/** Returns a ready-made reply, or null when the message should go to the AI. */
internal fun trivialReply(message: String): String? {
    if (CrisisSupport.needsAnySupport(message)) return null
    val text = normalise(message)
    val hindi = Lang.current == AppLanguage.HINDI
    if (text.isEmpty() || text.length > 30) return null
    return when (text) {
        in greetings -> pick(if (hindi) greetingRepliesHi else greetingReplies, text)
        in thanks -> pick(if (hindi) thanksRepliesHi else thanksReplies, text)
        in goodbyes -> pick(if (hindi) goodbyeRepliesHi else goodbyeReplies, text)
        in acknowledgements -> pick(if (hindi) acknowledgementRepliesHi else acknowledgementReplies, text)
        else -> null
    }
}
