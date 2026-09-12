package com.nexappra.filerecovery.domain.model

data class AppLanguage(
    val code: String,
    val displayName: String,
)

object SupportedLanguages {

    val English = AppLanguage(
        code = "en",
        displayName = "English",
    )

    val Spanish = AppLanguage(
        code = "es",
        displayName = "Español (Spanish)",
    )

    val French = AppLanguage(
        code = "fr",
        displayName = "Français (French)",
    )

    val German = AppLanguage(
        code = "de",
        displayName = "Deutsch (German)",
    )

    val Italian = AppLanguage(
        code = "it",
        displayName = "Italiano (Italian)",
    )

    val Portuguese = AppLanguage(
        code = "pt",
        displayName = "Português (Portuguese)",
    )

    val Russian = AppLanguage(
        code = "ru",
        displayName = "Русский (Russian)",
    )

    val Chinese = AppLanguage(
        code = "zh",
        displayName = "中文 (Chinese)",
    )

    val Japanese = AppLanguage(
        code = "ja",
        displayName = "日本語 (Japanese)",
    )

    val Korean = AppLanguage(
        code = "ko",
        displayName = "한국어 (Korean)",
    )

    val Arabic = AppLanguage(
        code = "ar",
        displayName = "العربية (Arabic)",
    )

    val Hindi = AppLanguage(
        code = "hi",
        displayName = "हिन्दी (Hindi)",
    )

    val Turkish = AppLanguage(
        code = "tr",
        displayName = "Türkçe (Turkish)",
    )

    val Indonesian = AppLanguage(
        code = "id",
        displayName = "Bahasa Indonesia",
    )

    val Urdu = AppLanguage(
        code = "ur",
        displayName = "اردو (Urdu)",
    )

    val all = listOf(
        English,
        Spanish,
        French,
        German,
        Italian,
        Portuguese,
        Russian,
        Chinese,
        Japanese,
        Korean,
        Arabic,
        Hindi,
        Turkish,
        Indonesian,
        Urdu,
    )

    fun findByCode(code: String): AppLanguage {
        return all.firstOrNull {
            it.code == code
        } ?: English
    }
}