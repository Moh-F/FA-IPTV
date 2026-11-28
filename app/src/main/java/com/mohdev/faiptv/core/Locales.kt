package com.mohdev.faiptv.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Language handling. The app language is stored as a plain tag ("ar", "en", "de") so Android accepts it
 * (a tag with extensions like "ar-u-nu-latn" is rejected by the per-app language system on Android 13+).
 * Western digits (0123456789) in Arabic: numbers are inserted as text, dates are formatted with num().
 */
object Locales {
    /** "" = system, "ar", "en", "de" */
    fun current(): String {
        val l = AppCompatDelegate.getApplicationLocales()
        return if (l.isEmpty) "" else l[0]?.language ?: ""
    }

    fun set(lang: String) {
        AppCompatDelegate.setApplicationLocales(if (lang.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang))
    }

    /** Repairs a stored "ar-u-nu-latn" from version 1.1/1.2. */
    fun repair() {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.contains("-u-")) set(tags.substringBefore("-u-"))
    }

    /*
     * Latin digits (0123456789) in Arabic: the activity configuration is NOT touched any more.
     * Changing it (1.3) made AppCompat see a different locale than the stored one on Android 7–12 and recreate
     * the activity again and again — the app hung. Numbers in texts are inserted as %s (Latin), dates use num().
     */

    /** Same locale, Latin digits. Use for all number/date formatting. */
    fun num(l: Locale = Locale.getDefault()): Locale =
        if (l.getUnicodeLocaleType("nu") == "latn") l else Locale.Builder().setLocale(l).setUnicodeLocaleKeyword("nu", "latn").build()
}
