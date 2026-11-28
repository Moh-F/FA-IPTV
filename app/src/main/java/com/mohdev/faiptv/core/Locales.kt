package com.mohdev.faiptv.core

import android.app.Activity
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Language handling. The app language is stored as a plain tag ("ar", "en", "de") so Android accepts it
 * (a tag with extensions like "ar-u-nu-latn" is rejected by the per-app language system on Android 13+).
 * Western digits (0123456789) in Arabic are applied separately to the activity's configuration.
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

    /** Arabic UI with Latin digits: patch the activity configuration (string formatting, dates). */
    fun applyDigits(a: Activity) {
        runCatching {
            val res = a.resources
            val cfg = Configuration(res.configuration)
            val l = cfg.locales[0] ?: return
            if (l.language == "ar" && l.getUnicodeLocaleType("nu") != "latn") {
                cfg.setLocale(num(l))
                @Suppress("DEPRECATION") res.updateConfiguration(cfg, res.displayMetrics)
            }
        }
        val d = Locale.getDefault()
        if (d.language == "ar" && d.getUnicodeLocaleType("nu") != "latn") Locale.setDefault(num(d))
    }

    /** Same locale, Latin digits. Use for all number/date formatting. */
    fun num(l: Locale = Locale.getDefault()): Locale =
        if (l.getUnicodeLocaleType("nu") == "latn") l else Locale.Builder().setLocale(l).setUnicodeLocaleKeyword("nu", "latn").build()
}
