package com.example.tictactoe

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_LANG = "app_language"

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "en") ?: "en"
    }

    fun setLocale(context: Context, languageCode: String): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, languageCode).apply()
        return updateResources(context, languageCode)
    }

    fun wrapContext(context: Context): Context {
        val lang = getLanguage(context)
        return updateResources(context, lang)
    }

    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "ru" -> Locale("ru")
            "uz" -> Locale("uz")
            else -> Locale("en")
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
}

object ThemeHelper {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_DARK_MODE = "is_dark_mode"

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, true) // Dark mode default
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
        applyTheme(isDark)
    }

    fun applySavedTheme(context: Context) {
        val isDark = isDarkMode(context)
        applyTheme(isDark)
    }

    private fun applyTheme(isDark: Boolean) {
        val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
