package app.messages.utility

import android.content.Context
import android.os.Build
import app.messages.preferences.PreferenceHelper
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context, languageCode: String): Context {
        syncLanguageToFlutter(context, languageCode)
        return updateResources(context, languageCode)
    }

    fun syncLanguageToFlutter(context: Context, languageCode: String) {
        try {
            val normalizedCode = if (languageCode == "in") "id" else languageCode
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .edit()
                .putString("flutter.selectedLanguageCode", normalizedCode)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }

    fun Context.getLocalizedContext(): Context {

        val langCode = PreferenceHelper.getInstance(this).getSelectedLanguageCode()
        if (langCode.isNullOrEmpty()) return this

        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return createConfigurationContext(config)
    }
}
