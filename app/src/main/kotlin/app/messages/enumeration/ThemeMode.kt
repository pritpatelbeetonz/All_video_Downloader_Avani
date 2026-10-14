package app.messages.enumeration

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import app.messages.R

enum class ThemeMode(val value: Int) {
    SYSTEM_DEFAULT(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.find { it.value == value } ?: ThemeMode.SYSTEM_DEFAULT
        }
    }
}


fun getThemeModeText(value: Int, context: Context): String {
    return when (value) {
        ThemeMode.SYSTEM_DEFAULT.value -> context.getString(R.string.system_default)
        ThemeMode.LIGHT.value -> context.getString(R.string.light_theme)
        ThemeMode.DARK.value -> context.getString(R.string.dark_theme)
        else -> {
            context.getString(R.string.system_default)
        }
    }
}


