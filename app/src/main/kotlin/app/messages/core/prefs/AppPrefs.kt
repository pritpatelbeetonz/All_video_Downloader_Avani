package app.messages.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "messages_prefs")

/** Light/Dark/System appearance choice (§5.2, §F12). */
enum class ThemeMode { System, Light, Dark }

/** Configurable conversation-list swipe action (§F10). */
enum class SwipeAction { Archive, Delete, MarkRead }

private object PrefKeys {
    val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    val SESSION_COUNT = intPreferencesKey("session_count")
    val LAST_APP_OPEN_AD = longPreferencesKey("last_app_open_ad")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val SWIPE_ACTION = stringPreferencesKey("swipe_action")
    val DELIVERY_REPORTS = booleanPreferencesKey("delivery_reports")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val MESSAGE_PREVIEW = booleanPreferencesKey("message_preview")
    val ADS_ENABLED_DEBUG = booleanPreferencesKey("ads_enabled_debug")
    val SURVEY_PRIORITIES = stringSetPreferencesKey("survey_priorities")
    val PREMIUM_LOCAL = booleanPreferencesKey("premium_local")
    val APP_OPEN_COUNT = intPreferencesKey("app_open_count")
    val APP_OPEN_COUNT_DAY = longPreferencesKey("app_open_count_day")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
    val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
}

/**
 * Strongly-typed accessor over [androidx.datastore.preferences.core.Preferences].
 *
 * One source for every pref key — never read DataStore by string elsewhere.
 */
@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasSeenOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.HAS_SEEN_ONBOARDING] ?: false }

    val sessionCount: Flow<Int> =
        context.dataStore.data.map { it[PrefKeys.SESSION_COUNT] ?: 0 }

    val lastAppOpenAdAt: Flow<Long?> =
        context.dataStore.data.map { it[PrefKeys.LAST_APP_OPEN_AD] }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { it[PrefKeys.HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun incrementSessionCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[PrefKeys.SESSION_COUNT] ?: 0
            prefs[PrefKeys.SESSION_COUNT] = current + 1
        }
    }

    suspend fun setLastAppOpenAdAt(epochMillis: Long) {
        context.dataStore.edit { it[PrefKeys.LAST_APP_OPEN_AD] = epochMillis }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[PrefKeys.THEME_MODE] ?: "") }
            .getOrDefault(ThemeMode.System)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[PrefKeys.THEME_MODE] = mode.name }
    }

    val swipeAction: Flow<SwipeAction> = context.dataStore.data.map {
        runCatching { SwipeAction.valueOf(it[PrefKeys.SWIPE_ACTION] ?: "") }
            .getOrDefault(SwipeAction.Archive)
    }

    suspend fun setSwipeAction(action: SwipeAction) {
        context.dataStore.edit { it[PrefKeys.SWIPE_ACTION] = action.name }
    }

    // Off by default: most carriers don't reliably return delivery reports, and a
    // never-arriving report makes a sent message look perpetually unconfirmed.
    val deliveryReports: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.DELIVERY_REPORTS] ?: false }

    suspend fun setDeliveryReports(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.DELIVERY_REPORTS] = enabled }
    }

    // Notification preferences (§F7), applied by MessageNotifier. Both default ON.
    // The master gate skips posting message notifications; preview hides the
    // sender + body (a generic "New message" is shown instead).
    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NOTIFICATIONS_ENABLED] = enabled }
    }

    val messagePreview: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.MESSAGE_PREVIEW] ?: true }

    suspend fun setMessagePreview(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.MESSAGE_PREVIEW] = enabled }
    }

    // Ads master switch, persisted so it survives relaunch — the Mobile Ads SDK
    // only initializes on the ads-enabled-at-startup path, so an in-memory flag
    // never took effect. Defaults to BuildConfig.DEBUG: debug builds show ads for
    // testing, release ships OFF (the real gate becomes Firebase Remote Config).
    val adsEnabledDebug: Flow<Boolean> =
        context.dataStore.data.map {
            it[PrefKeys.ADS_ENABLED_DEBUG] ?: app.messages.BuildConfig.DEBUG
        }

    suspend fun setAdsEnabledDebug(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.ADS_ENABLED_DEBUG] = enabled }
    }

    /** Feature priorities picked on the onboarding survey (§ "What matters most"). */
    val surveyPriorities: Flow<Set<String>> =
        context.dataStore.data.map { it[PrefKeys.SURVEY_PRIORITIES] ?: emptySet() }

    suspend fun setSurveyPriorities(values: Set<String>) {
        context.dataStore.edit { it[PrefKeys.SURVEY_PRIORITIES] = values }
    }

    /**
     * Locally-cached premium entitlement. The source of truth at maturity is
     * RevenueCat's CustomerInfo (re-validated on launch); this cache keeps the
     * UI correct offline and is what a debug build / successful purchase sets.
     */
    val premiumLocal: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.PREMIUM_LOCAL] ?: false }

    suspend fun setPremiumLocal(value: Boolean) {
        context.dataStore.edit { it[PrefKeys.PREMIUM_LOCAL] = value }
    }

    // ── App-open ad daily cap accounting ───────────────────────────────────────
    private fun epochDay(nowMs: Long): Long = nowMs / 86_400_000L

    suspend fun appOpenShownToday(nowMs: Long): Int {
        val prefs = context.dataStore.data.first()
        val day = prefs[PrefKeys.APP_OPEN_COUNT_DAY] ?: -1L
        return if (day == epochDay(nowMs)) prefs[PrefKeys.APP_OPEN_COUNT] ?: 0 else 0
    }

    suspend fun incrementAppOpenShownToday(nowMs: Long) {
        val today = epochDay(nowMs)
        context.dataStore.edit { prefs ->
            val day = prefs[PrefKeys.APP_OPEN_COUNT_DAY] ?: -1L
            val current = if (day == today) prefs[PrefKeys.APP_OPEN_COUNT] ?: 0 else 0
            prefs[PrefKeys.APP_OPEN_COUNT_DAY] = today
            prefs[PrefKeys.APP_OPEN_COUNT] = current + 1
        }
    }

    // ── App lock (§F16) ─────────────────────────────────────────────────────────
    val appLockEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.APP_LOCK_ENABLED] ?: false }

    val appLockBiometric: Flow<Boolean> =
        context.dataStore.data.map { it[PrefKeys.APP_LOCK_BIOMETRIC] ?: false }

    /** Encoded PIN credential ("saltHex:hashHex"), or null when no PIN is set. */
    val appLockPin: Flow<String?> =
        context.dataStore.data.map { it[PrefKeys.APP_LOCK_PIN] }

    suspend fun appLockPinNow(): String? =
        context.dataStore.data.first()[PrefKeys.APP_LOCK_PIN]

    suspend fun setAppLockBiometric(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.APP_LOCK_BIOMETRIC] = enabled }
    }

    /** Sets the PIN credential and turns the lock on in one write. */
    suspend fun setAppLockPin(encodedCredential: String) {
        context.dataStore.edit {
            it[PrefKeys.APP_LOCK_PIN] = encodedCredential
            it[PrefKeys.APP_LOCK_ENABLED] = true
        }
    }

    /** Clears the PIN, biometric flag, and lock state together. */
    suspend fun clearAppLock() {
        context.dataStore.edit {
            it.remove(PrefKeys.APP_LOCK_PIN)
            it[PrefKeys.APP_LOCK_ENABLED] = false
            it[PrefKeys.APP_LOCK_BIOMETRIC] = false
        }
    }
}
