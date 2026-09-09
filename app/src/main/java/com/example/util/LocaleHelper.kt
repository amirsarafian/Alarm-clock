package com.example.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    const val LANG_SYSTEM = "system"
    const val LANG_ENGLISH = "en"
    const val LANG_PERSIAN = "fa"

    fun getSelectedLanguage(context: Context): String {
        return LANG_SYSTEM
    }

    fun setSelectedLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun isPersian(context: Context): Boolean {
        val sysLang = Locale.getDefault().language.lowercase()
        return sysLang.startsWith("fa") || sysLang == "pes" || sysLang == "fas"
    }

    fun getActiveLocale(context: Context): Locale {
        val sysLang = Locale.getDefault().language.lowercase()
        return if (sysLang.startsWith("fa") || sysLang == "pes" || sysLang == "fas") {
            Locale("fa")
        } else {
            Locale.getDefault()
        }
    }

    fun createLocalizedContext(baseContext: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return baseContext.createConfigurationContext(config)
    }
}
