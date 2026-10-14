package app.messages.preferences

import android.content.Context
import android.content.SharedPreferences
import app.messages.enumeration.NotificationPreviewMode
import app.messages.enumeration.SwipeActions
import app.messages.enumeration.ThemeMode

class PreferenceHelper private constructor(context: Context) {

    private val prefs: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "your_app_prefs"

        @Volatile
        private var INSTANCE: PreferenceHelper? = null

        fun getInstance(context: Context): PreferenceHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceHelper(context).also { INSTANCE = it }
            }

        // Preference keys
        const val KEY_THEME_MODE = "KEY_THEME_MODE"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_IS_PREMIUM = "is_premium"

        const val KEY_PERMISSION_DONE = "permission_done"
        const val KEY_LANGUAGE_SELECTED = "language_selected"
        const val KEY_SELECTED_LANGUAGE_CODE = "selected_language_code"
        const val KEY_SELECTED_SIM_SUB_ID = "selected_sim_sub_id"  // -1 = not set
        private const val CONFIG_JSON = "config_json"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_NOTIFICATION_PREVIEW_MODE = "notification_preview_mode"
        const val KEY_NOTIFICATION_WAKE_SCREEN = "notification_wake_screen"
        const val KEY_NOTIFICATION_ACTION_BUTTON_1 = "notification_action_button_1"
        const val KEY_NOTIFICATION_ACTION_BUTTON_2 = "notification_action_button_2"
        const val KEY_NOTIFICATION_ACTION_BUTTON_3 = "notification_action_button_3"
        const val KEY_NOTIFICATION_QUICK_REPLY_ENABLED = "notification_quick_reply_enabled"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_RIGHT_SWIPE_ACTION = "right_swipe_action"
        const val KEY_LEFT_SWIPE_ACTION = "left_swipe_action"

        const val KEY_DELAYED_SENDING = "delayed_sending"
        const val KEY_DELIVERY_REPORTS_ENABLED = "delivery_reports_enabled"
        const val KEY_STRIP_ACCENTS_ENABLED = "strip_accents_enabled"
        const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        const val KEY_IS_FIRST_APP_LAUNCH = "is_first_app_launch"
        const val KEY_APP_OPEN_COUNT = "app_open_count"
        const val KEY_PREMIUM_INTRO_SHOWN = "premium_intro_shown"
        const val KEY_IS_LAUNCHER_MODE = "is_launcher_mode"
        const val KEY_SMS_PERMISSION_REQUESTED = "sms_permission_requested"
        const val KEY_DROP_MESSAGES_ENABLED = "drop_messages_enabled"
        const val KEY_MMS_COMPRESSION_LIMIT = "mms_compression_limit"
        const val KEY_AUTO_DELETE_PERIOD = "auto_delete_period"
        const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
        const val KEY_ONESIGNAL_APP_ID = "onesignal_app_id"
        const val KEY_FACEBOOK_APP_ID = "facebook_app_id"
        const val KEY_FACEBOOK_CLIENT_TOKEN = "facebook_client_token"

        const val KEY_EVENT_SET_LAUNCHER_LOGGED = "key_event_set_launcher_logged"
        const val KEY_EVENT_LANGUAGE_LOGGED = "key_event_language_logged"
        const val KEY_EVENT_DEFAULT_SMS_LOGGED = "key_event_default_sms_logged"
    }

    fun isEventLogged(key: String): Boolean = prefs.getBoolean(key, false)
    fun setEventLogged(key: String, logged: Boolean) = prefs.edit().putBoolean(key, logged).apply()

    fun getBackupFolderUri(): String? {
        return prefs.getString(KEY_BACKUP_FOLDER_URI, null)
    }

    fun setBackupFolderUri(uri: String?) {
        prefs.edit().putString(KEY_BACKUP_FOLDER_URI, uri).apply()
    }

    fun getAutoDeletePeriod(): Int {
        // 0 = Never, 7 = 7 days, 30 = 30 days, 90 = 90 days, 365 = 1 year
        return prefs.getInt(KEY_AUTO_DELETE_PERIOD, 0)
    }

    fun setAutoDeletePeriod(days: Int) {
        prefs.edit().putInt(KEY_AUTO_DELETE_PERIOD, days).apply()
    }

    fun getMmsCompressionLimit(): Int {
        // -1 = Automatic, 0 = No compression, > 0 = Limit in KB (e.g. 100, 200, 300)
        return prefs.getInt(KEY_MMS_COMPRESSION_LIMIT, -1)
    }

    fun setMmsCompressionLimit(limitKb: Int) {
        prefs.edit().putInt(KEY_MMS_COMPRESSION_LIMIT, limitKb).apply()
    }

    fun isDropMessagesEnabled(): Boolean {
        return prefs.getBoolean(KEY_DROP_MESSAGES_ENABLED, true)
    }

    fun setDropMessagesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DROP_MESSAGES_ENABLED, enabled).apply()
    }

    fun setSelectedSimSubscriptionId(subId: Long) {
        prefs.edit().putLong(KEY_SELECTED_SIM_SUB_ID, subId).apply()
    }

    fun getSelectedSimSubscriptionId(): Long {
        return prefs.getLong(KEY_SELECTED_SIM_SUB_ID, -1L)
    }

    fun clearSelectedSim() {
        prefs.edit().remove(KEY_SELECTED_SIM_SUB_ID).apply()
    }

    fun isFirstAppLaunch(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_APP_LAUNCH, true)
    }

    fun setFirstAppLaunch(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_IS_FIRST_APP_LAUNCH, isFirst).apply()
    }

    fun isPremiumIntroShown(): Boolean {
        return prefs.getBoolean(KEY_PREMIUM_INTRO_SHOWN, false)
    }

    fun setPremiumIntroShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM_INTRO_SHOWN, shown).apply()
    }

    fun isLauncherModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_LAUNCHER_MODE, false)
    }

    fun setLauncherModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LAUNCHER_MODE, enabled).apply()
    }

    fun isSmsPermissionRequested(): Boolean {
        return prefs.getBoolean(KEY_SMS_PERMISSION_REQUESTED, false)
    }

    fun setSmsPermissionRequested(requested: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_PERMISSION_REQUESTED, requested).apply()
    }

    fun getThemeMode(): Int {
        // Mirrors the app-wide appearance choice (AppPrefs.themeMode) for the
        // View-based screens — launcher, dashboard widgets, chat. The default
        // matches that setting's own default, "System", so the very first frame
        // agrees with what Compose renders.
        return prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM_DEFAULT.value)
    }

    fun setThemMode(themeMode: Int) {
        prefs.edit()
            .putInt(KEY_THEME_MODE, themeMode)
            .apply()
    }


    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun isOnboardingDone(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setIsPremium(isPremium: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply()
    }

    fun isPremium(): Boolean {
        return prefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun setPermissionDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSION_DONE, done).apply()
    }

    fun isPermissionDone(): Boolean {
        return prefs.getBoolean(KEY_PERMISSION_DONE, false)
    }

    fun setLanguageSelected(selected: Boolean) {
        prefs.edit().putBoolean(KEY_LANGUAGE_SELECTED, selected).apply()
    }

    fun isLanguageSelected(): Boolean {
        return prefs.getBoolean(KEY_LANGUAGE_SELECTED, false)
    }

    fun setSelectedLanguageCode(code: String) {
        prefs.edit().putString(KEY_SELECTED_LANGUAGE_CODE, code).apply()
    }

    fun getSelectedLanguageCode(): String {
        return prefs.getString(KEY_SELECTED_LANGUAGE_CODE, "en") ?: "en"
    }

    fun setConfigJSON(jsonStr: String) {
        prefs.edit().putString(CONFIG_JSON, jsonStr).apply()
    }

    fun getConfigJSON(): String? {
        return prefs.getString(CONFIG_JSON, null) ?: null
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    /**
     * 0 = Show name and message, 1 = Show name, 2 = Hide contents
     */
    fun setNotificationPreviewMode(mode: Int) {
        prefs.edit().putInt(KEY_NOTIFICATION_PREVIEW_MODE, mode).apply()
    }

    fun getNotificationPreviewMode(): Int {
        return prefs.getInt(
            KEY_NOTIFICATION_PREVIEW_MODE,
            NotificationPreviewMode.SHOW_NAME_AND_MESSAGES.value
        )
    }

    fun setNotificationWakeScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_WAKE_SCREEN, enabled).apply()
    }

    fun getNotificationWakeScreen(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_WAKE_SCREEN, false)
    }

    /**
     * SwipeActions.value: 0=None, 1=Archive, 2=Delete, 3=Block, 4=Call, 5=Mark read, 6=Mark unread, 7=Reply
     */
    fun setNotificationActionButton(index: Int, action: Int) {
        val key = when (index) {
            1 -> KEY_NOTIFICATION_ACTION_BUTTON_1
            2 -> KEY_NOTIFICATION_ACTION_BUTTON_2
            else -> KEY_NOTIFICATION_ACTION_BUTTON_3
        }
        prefs.edit().putInt(key, action).apply()
    }

    fun getNotificationActionButton(index: Int): Int {
        val key = when (index) {
            1 -> KEY_NOTIFICATION_ACTION_BUTTON_1
            2 -> KEY_NOTIFICATION_ACTION_BUTTON_2
            else -> KEY_NOTIFICATION_ACTION_BUTTON_3
        }
        val defaultValue = when (index) {
            1 -> SwipeActions.MARK_READ.value
            2 -> SwipeActions.REPLY.value
            else -> SwipeActions.NONE.value
        }
        return prefs.getInt(key, defaultValue)
    }

    fun setNotificationQuickReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_QUICK_REPLY_ENABLED, enabled).apply()
    }

    fun getNotificationQuickReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_QUICK_REPLY_ENABLED, true)
    }

    fun setFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
    }

    fun getFontSize(): Float {
        return prefs.getFloat(KEY_FONT_SIZE, 1.0f)
    }

    fun setRightSwipeAction(size: Int) {
        prefs.edit().putInt(KEY_RIGHT_SWIPE_ACTION, size).apply()
    }

    fun getRightSwipeAction(): Int {
        return prefs.getInt(KEY_RIGHT_SWIPE_ACTION, SwipeActions.ARCHIVE.value)
    }

    fun setLeftSwipeAction(size: Int) {
        prefs.edit().putInt(KEY_LEFT_SWIPE_ACTION, size).apply()
    }

    fun getLeftSwipeAction(): Int {
        return prefs.getInt(KEY_LEFT_SWIPE_ACTION, SwipeActions.DELETE.value)
    }

    fun setDelayedSendingInSeconds(size: Int) {
        prefs.edit().putInt(KEY_DELAYED_SENDING, size).apply()
    }

    fun getDelayedSendingInSeconds(): Int {
        return prefs.getInt(KEY_DELAYED_SENDING, 0)
    }

    fun setDeliveryReportsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DELIVERY_REPORTS_ENABLED, enabled).apply()
    }

    fun getDeliveryReportsEnabled(): Boolean {
        return prefs.getBoolean(KEY_DELIVERY_REPORTS_ENABLED, false)
    }

    fun setStripAccentsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRIP_ACCENTS_ENABLED, enabled).apply()
    }

    fun getStripAccentsEnabled(): Boolean {
        return prefs.getBoolean(KEY_STRIP_ACCENTS_ENABLED, false)
    }

    fun setLastBackupTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, time).apply()
    }

    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    fun setAppOpenCount(count: Int) {
        prefs.edit().putInt(KEY_APP_OPEN_COUNT, count).apply()
    }

    fun getAppOpenCount(): Int {
        return prefs.getInt(KEY_APP_OPEN_COUNT, 0)
    }

    fun getOneSignalAppId(): String {
        return prefs.getString(KEY_ONESIGNAL_APP_ID, "") ?: ""
    }

    fun setOneSignalAppId(appId: String) {
        prefs.edit().putString(KEY_ONESIGNAL_APP_ID, appId).apply()
    }

    fun getFacebookAppId(): String {
        return prefs.getString(KEY_FACEBOOK_APP_ID, "") ?: ""
    }

    fun setFacebookAppId(appId: String) {
        prefs.edit().putString(KEY_FACEBOOK_APP_ID, appId).apply()
    }

    fun getFacebookClientToken(): String {
        return prefs.getString(KEY_FACEBOOK_CLIENT_TOKEN, "") ?: ""
    }

    fun setFacebookClientToken(token: String) {
        prefs.edit().putString(KEY_FACEBOOK_CLIENT_TOKEN, token).apply()
    }

    // --- Launcher Settings ---

    fun getHomeHideLabels(): Boolean = prefs.getBoolean("home_hide_labels", false)
    fun setHomeHideLabels(hide: Boolean) = prefs.edit().putBoolean("home_hide_labels", hide).apply()

    fun getHomeTextSize(): Int = prefs.getInt("home_text_size", 12)
    fun setHomeTextSize(sizePt: Int) = prefs.edit().putInt("home_text_size", sizePt).apply()

    fun getHomeIconSize(): Int = prefs.getInt("home_icon_size", 55)
    fun setHomeIconSize(sizeDp: Int) = prefs.edit().putInt("home_icon_size", sizeDp).apply()

    fun getDrawerHideLabels(): Boolean = prefs.getBoolean("drawer_hide_labels", false)
    fun setDrawerHideLabels(hide: Boolean) = prefs.edit().putBoolean("drawer_hide_labels", hide).apply()

    fun getDrawerTextSize(): Int = prefs.getInt("drawer_text_size", 12)
    fun setDrawerTextSize(sizePt: Int) = prefs.edit().putInt("drawer_text_size", sizePt).apply()

    fun getDrawerIconSize(): Int = prefs.getInt("drawer_icon_size", 54)
    fun setDrawerIconSize(sizeDp: Int) = prefs.edit().putInt("drawer_icon_size", sizeDp).apply()

    fun getDrawerSearchBarEnabled(): Boolean = prefs.getBoolean("drawer_search_bar_enabled", true)
    fun setDrawerSearchBarEnabled(enabled: Boolean) = prefs.edit().putBoolean("drawer_search_bar_enabled", enabled).apply()

    fun getSearchAutoOpenKeyboard(): Boolean = prefs.getBoolean("search_auto_open_keyboard", false)
    fun setSearchAutoOpenKeyboard(enabled: Boolean) = prefs.edit().putBoolean("search_auto_open_keyboard", enabled).apply()

    fun getSearchCloseDrawerOnAppOpen(): Boolean = prefs.getBoolean("search_close_drawer_on_app_open", true)
    fun setSearchCloseDrawerOnAppOpen(enabled: Boolean) = prefs.edit().putBoolean("search_close_drawer_on_app_open", enabled).apply()

    fun getSearchAppSuggestionsEnabled(): Boolean = prefs.getBoolean("search_app_suggestions_enabled", true)
    fun setSearchAppSuggestionsEnabled(enabled: Boolean) = prefs.edit().putBoolean("search_app_suggestions_enabled", enabled).apply()

    fun getNavigationBarVisible(): Boolean = prefs.getBoolean("navigation_bar_visible", true)
    fun setNavigationBarVisible(visible: Boolean) = prefs.edit().putBoolean("navigation_bar_visible", visible).apply()

    // Gestures: 0=Do Nothing, 1=Open app drawer, 2=Open launcher setting, 3=Open home screen pop-up, 4=Open global search
    fun getGestureDoubleTap(): Int = prefs.getInt("gesture_double_tap", 0)
    fun setGestureDoubleTap(action: Int) = prefs.edit().putInt("gesture_double_tap", action).apply()

    fun getGestureTapAndHold(): Int = prefs.getInt("gesture_tap_and_hold", 3)
    fun setGestureTapAndHold(action: Int) = prefs.edit().putInt("gesture_tap_and_hold", action).apply()

    fun getGestureHomeButton(): Int = prefs.getInt("gesture_home_button", 0)
    fun setGestureHomeButton(action: Int) = prefs.edit().putInt("gesture_home_button", action).apply()

    fun getGestureBackButton(): Int = prefs.getInt("gesture_back_button", 0)
    fun setGestureBackButton(action: Int) = prefs.edit().putInt("gesture_back_button", action).apply()

    fun getPackageInstallScan(): Boolean = prefs.getBoolean("package_install_scan", true)
    fun setPackageInstallScan(enabled: Boolean) = prefs.edit().putBoolean("package_install_scan", enabled).apply()

    fun getPackageUninstallScan(): Boolean = prefs.getBoolean("package_uninstall_scan", true)
    fun setPackageUninstallScan(enabled: Boolean) = prefs.edit().putBoolean("package_uninstall_scan", enabled).apply()
}