package app.messages.utility

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import app.messages.enumeration.ThemeMode
import app.messages.preferences.PreferenceHelper

/**
 * Single application point for the user's appearance choice.
 *
 * The choice itself lives in `AppPrefs.themeMode` (DataStore), which the Compose
 * UI observes directly. Everything drawn with Views — the launcher home, the app
 * drawer, the dashboard widgets, chat — follows [AppCompatDelegate]'s night mode
 * instead, so the value is mirrored into [PreferenceHelper] and pushed into the
 * delegate here. That mirror is what keeps the launcher from staying light while
 * the messages UI is dark.
 */
object ThemeUtils {

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(PreferenceHelper.getInstance(context).getThemeMode())
    }

    fun saveTheme(context: Context, theme: Int) {
        PreferenceHelper.getInstance(context).setThemMode(theme)

        applyTheme(context)
    }

    /** Night-mode constant for an [app.messages.core.prefs.ThemeMode] choice. */
    fun nightModeFor(mode: app.messages.core.prefs.ThemeMode): Int = when (mode) {
        app.messages.core.prefs.ThemeMode.System -> ThemeMode.SYSTEM_DEFAULT.value
        app.messages.core.prefs.ThemeMode.Light -> ThemeMode.LIGHT.value
        app.messages.core.prefs.ThemeMode.Dark -> ThemeMode.DARK.value
    }

    /**
     * Persists and applies the appearance choice for the View-based screens.
     * No-ops when nothing changed, so it is safe to call on every emission — the
     * delegate recreates every started Activity when the mode actually moves.
     */
    fun applyThemeMode(context: Context, mode: app.messages.core.prefs.ThemeMode) {
        val nightMode = nightModeFor(mode)
        val prefs = PreferenceHelper.getInstance(context)
        if (prefs.getThemeMode() != nightMode) {
            prefs.setThemMode(nightMode)
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
