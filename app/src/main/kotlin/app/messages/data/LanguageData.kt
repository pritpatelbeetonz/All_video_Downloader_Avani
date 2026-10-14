package app.messages.data

import app.messages.data.model.LanguageModel

object LanguageData {
    fun getCountries() = listOf(
        LanguageModel("English", "English (Default)", "en", false, "#1C73E8"),
        LanguageModel("Spanish", "Spanish (español)", "es", false, "#E53935"),
        LanguageModel("German", "German (Deutsch)", "de", false, "#43A047"),
        LanguageModel("French", "French (Français)", "fr", false, "#1E88E5"),
        LanguageModel("Arabic", "Arabic (العربية)", "ar", false, "#8E24AA"),
        LanguageModel("Russian", "Russian (Русский)", "ru", false, "#00ACC1"),
        LanguageModel("Hindi", "Hindi (हिन्दी)", "hi", false, "#FF9F0A"),
        LanguageModel("Portuguese", "Portuguese (Português)", "pt", false, "#FB8C00"),
        LanguageModel("Japanese", "Japanese (日本語)", "ja", false, "#D81B60"),
        LanguageModel("Korean", "Korean (한국어)", "ko", false, "#5E35B1"),
        LanguageModel("Chinese", "Chinese (中文)", "zh", false, "#F4511E"),
        LanguageModel("Turkish", "Turkish (Türkçe)", "tr", false, "#3949AB"),
        LanguageModel("Dutch", "Dutch (Nederlands)", "nl", false, "#00897B"),
        LanguageModel("Vietnamese", "Vietnamese (Tiếng Việt)", "vi", false, "#7CB342"),
        LanguageModel("Indonesian", "Indonesian (Bahasa Indonesia)", "in", false, "#F44649"),
        LanguageModel("Thai", "Thai (ไทย)", "th", false, "#4C46F4"),
        LanguageModel("Malay", "Malay (Bahasa Melayu)", "ms", false, "#009688"),
        LanguageModel("Punjabi", "Punjabi (ਪੰਜਾਬੀ)", "pa", false, "#C0CA33")
    )

    fun getLanguageByCode(code: String): LanguageModel? {
        return getCountries().find { it.code == code }
    }
}