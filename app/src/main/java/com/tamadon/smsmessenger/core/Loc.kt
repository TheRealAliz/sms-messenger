package com.tamadon.smsmessenger.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** In-app language handling (English / Persian / follow system). */
object Loc {
    const val FA = "fa"
    const val EN = "en"

    /** The language actually shown: Persian when chosen, or when the device is Persian; else English. */
    fun effective(pref: String): String = when (pref) {
        FA -> FA
        EN -> EN
        else -> {
            val sys = Resources.getSystem().configuration.locales.get(0)?.language
            if (sys == "fa") FA else EN
        }
    }

    fun isPersian(pref: String) = effective(pref) == FA

    /** A context whose resources use the app language. Used by the activity and by background code. */
    fun wrap(base: Context, pref: String): Context {
        val locale = Locale(effective(pref))
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }
}
