package com.example.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val labelEn: String, val labelRu: String) {
    SYSTEM("System (EN/RU)", "Системный (EN/RU)"),
    EN("English", "English"),
    RU("Русский", "Русский");

    fun label(isRu: Boolean): String = if (isRu) labelRu else labelEn
}

val LocalIsRussian = staticCompositionLocalOf { false }

object AppLocaleHelper {

    fun isRussian(language: AppLanguage = AppLanguage.EN): Boolean {
        return when (language) {
            AppLanguage.EN -> false
            AppLanguage.RU -> true
            AppLanguage.SYSTEM -> Locale.getDefault().language.equals("ru", ignoreCase = true)
        }
    }

    fun localizedContext(base: Context, language: AppLanguage): Context {
        if (language == AppLanguage.SYSTEM) return base
        val targetLocale = if (language == AppLanguage.RU) Locale("ru") else Locale.ENGLISH
        val config = Configuration(base.resources.configuration)
        config.setLocale(targetLocale)
        return base.createConfigurationContext(config)
    }

    fun tr(isRu: Boolean, en: String, ru: String): String = if (isRu) ru else en
}
