package com.mindtrack.app

import androidx.core.content.edit
import java.util.Locale

/**
 * Languages the app can show. Text is written in English in the code and looked up through [tr];
 * anything that has no translation yet simply stays in English, so a missing string is never blank.
 *
 * To add a language: add an entry here, create a `Translations<Name>.kt` map like [hindiStrings],
 * and register it in [translationsFor].
 */
internal enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी")
}

internal object Lang {
    /** Set when the activity starts. Changing language restarts the activity, so this never changes mid-screen. */
    var current: AppLanguage = AppLanguage.ENGLISH
}

private val hindiAll: Map<String, String> by lazy { hindiStrings + hindiLongStrings }

private fun translationsFor(language: AppLanguage): Map<String, String> = when (language) {
    AppLanguage.ENGLISH -> emptyMap()
    AppLanguage.HINDI -> hindiAll
}

/** Translates [text] into the current language, or returns it unchanged when there is no translation. */
internal fun tr(text: String): String {
    if (Lang.current == AppLanguage.ENGLISH) return text
    return translationsFor(Lang.current)[text] ?: text
}

/** Like [tr] for text with placeholders, written as %1$s, %2$d and so on. */
internal fun trf(template: String, vararg args: Any?): String =
    String.format(Locale.getDefault(), tr(template), *args)

internal fun readLanguage(prefs: android.content.SharedPreferences): AppLanguage {
    val saved = prefs.getString("language", null)
    AppLanguage.entries.firstOrNull { it.code == saved }?.let { return it }
    // First launch: follow the phone's language when we support it.
    return AppLanguage.entries.firstOrNull { it.code == Locale.getDefault().language } ?: AppLanguage.ENGLISH
}

internal fun saveLanguage(prefs: android.content.SharedPreferences, language: AppLanguage) {
    prefs.edit { putString("language", language.code) }
}

/** The locale to use for dates and number words, following the app's language. */
internal fun appLocale(): Locale = when (Lang.current) {
    AppLanguage.ENGLISH -> Locale.getDefault()
    AppLanguage.HINDI -> Locale("hi", "IN")
}
