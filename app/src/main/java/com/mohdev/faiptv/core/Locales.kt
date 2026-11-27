package com.mohdev.faiptv.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Language handling. Arabic always uses Western digits (0123456789) as the user wants. */
object Locales {
    /** "" = system, "ar", "en", "de" */
    fun current(): String {
        val l = AppCompatDelegate.getApplicationLocales()
        return if (l.isEmpty) "" else l[0]?.language ?: ""
    }

    fun set(lang: String) {
        val tag = when (lang) { "" -> ""; "ar" -> "ar-u-nu-latn"; else -> lang }
        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        fixDigits()
    }

    /** If the UI ends up in Arabic (also via the system language), switch it to Arabic with Latin digits. */
    fun fixDigits() {
        val l = AppCompatDelegate.getApplicationLocales()
        val loc = if (l.isEmpty) Locale.getDefault() else l[0] ?: return
        if (loc.language == "ar" && loc.getUnicodeLocaleType("nu") != "latn") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar-u-nu-latn"))
        }
        val d = Locale.getDefault()
        if (d.language == "ar" && d.getUnicodeLocaleType("nu") != "latn") Locale.setDefault(num(d))
    }

    /** Same locale, Latin digits. Use for all number/date formatting. */
    fun num(l: Locale = Locale.getDefault()): Locale =
        if (l.getUnicodeLocaleType("nu") == "latn") l else Locale.Builder().setLocale(l).setUnicodeLocaleKeyword("nu", "latn").build()
}
