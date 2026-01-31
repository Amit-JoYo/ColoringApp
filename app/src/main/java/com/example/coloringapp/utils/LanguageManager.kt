package com.example.coloringapp.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import java.util.Locale

private const val TAG = "LanguageManager"

/**
 * Manager for app language settings.
 * Supports system default, English, and Hebrew.
 */
object LanguageManager {
    
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    enum class Language(val code: String, val displayName: String, val nativeName: String) {
        SYSTEM("system", "System Default", "System Default"),
        ENGLISH("en", "English", "English"),
        HEBREW("iw", "Hebrew", "עברית")
    }
    
    /**
     * Get the saved language preference
     */
    fun getSelectedLanguage(context: Context): Language {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getString(KEY_LANGUAGE, Language.SYSTEM.code) ?: Language.SYSTEM.code
            Language.entries.find { it.code == code } ?: Language.SYSTEM
        } catch (e: Exception) {
            Log.e(TAG, "Error getting language preference", e)
            Language.SYSTEM
        }
    }
    
    /**
     * Save language preference and apply it
     */
    fun setLanguage(context: Context, language: Language) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_LANGUAGE, language.code)
            }
            applyLanguage(language)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language", e)
        }
    }
    
    /**
     * Apply the language using AppCompat's per-app language feature
     */
    fun applyLanguage(language: Language) {
        try {
            val localeList = if (language == Language.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.code)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying language", e)
        }
    }
    
    /**
     * Initialize language on app start
     */
    fun init(context: Context) {
        try {
            val savedLanguage = getSelectedLanguage(context)
            if (savedLanguage != Language.SYSTEM) {
                applyLanguage(savedLanguage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing language", e)
        }
    }
    
    /**
     * Get current effective locale
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    /**
     * Check if current language is RTL
     */
    fun isRtl(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
    }
}
