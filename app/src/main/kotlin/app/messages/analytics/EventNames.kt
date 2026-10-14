package app.messages.analytics

/**
 * Created by Dhara Golakiya on 26-01-2026
 * Email: dharagolakiya4@gmail.com
 */
object EventNames {

    const val APP_OPEN = "app_open"

    const val LAUNCHER_PERMISSION_GRANTED = "set_default_launcher_granted"
    const val LANGUAGE_SCREEN_DONE = "language_screen_done"
    const val SMS_PERMISSION_GRANTED = "set_asDefault_sms_permission_granted"

    const val PERMISSION_CLICK_ALLOW_PERMISSION = "permission_click_allow_permission"

    const val LANGUAGE_CLICK_DONE_ = "language_click_done_"

    //Home click events
    const val HOME_CLICK_ADD_NEW_MESSAGE = "home_click_add_new_message"
    const val HOME_CLICK_REMOVE_ADS = "home_click_remove_ads"
    const val HOME_CLICK_MENU = "home_click_menu"
    const val HOME_CLICK_MESSAGE = "home_click_message"

    const val SETTING_CLICK_AFTER_CALL_SCREEN = "setting_click_after_call_screen"


    // After Call Screen Click Events
    const val AFTER_CALL_CLICK_CHAT = "after_call_click_chat"
    const val AFTER_CALL_CLICK_MESSAGE = "aftercall_click_message"
    const val AFTER_CALL_CLICK_NOTIFICATION = "aftercall_click_notification"
    const val AFTER_CALL_CLICK_MORE_MENU = "aftercall_click_more_menu"
    const val AFTER_CALL_CLICK_CALL = "aftercall_click_call"
    const val AFTER_CALL_NOTIFICATION_CLICK_ADD = "aftercall_notification_click_add"
    const val AFTER_CALL_NOTIFICATION_CLICK_DELETE = "aftercall_notification_click_delete"
    const val AFTER_CALL_MENU_CLICK_ADD_CONTACT = "aftercall_menu_click_addcontact"
    const val AFTER_CALL_MENU_CLICK_MESSAGE = "aftercall_menu_click_message"
    const val AFTER_CALL_MENU_CLICK_MAIL = "aftercall_menu_click_mail"
    const val AFTER_CALL_MENU_CLICK_WEB = "aftercall_menu_click_web"
    const val AFTER_CALL_MENU_CLICK_CALENDAR = "aftercall_menu_click_calendar"

    //Onboarding click events
    const val ONBOARDING_CLICK_GET_STARTED = "onboarding_click_get_started"
    const val ONBOARDING_CLICK_NEXT = "onboarding_click_next"
    const val ONBOARDING_CLICK_SKIP = "onboarding_click_skip"

    //Settings click events
    const val SETTINGS_CLICK_FONT_SIZE = "settings_click_font_size"
    const val SETTINGS_CLICK_NOTIFICATIONS = "settings_click_notifications"
    const val SETTINGS_CLICK_DELAYED_SENDING = "settings_click_delayed_sending"
    const val SETTINGS_CLICK_CHOOSE_THEME = "settings_click_choose_theme"
    const val SETTINGS_CLICK_SWIPE_ACTIONS = "settings_click_swipe_actions"
    const val SETTINGS_CLICK_SYNC_MESSAGES = "settings_click_sync_messages"
    const val SETTINGS_CLICK_BACKUP_RESTORE = "settings_click_backup_restore"
    const val SETTINGS_CLICK_LANGUAGE = "settings_click_language"
    const val SETTINGS_CLICK_PRIVACY_POLICY = "settings_click_privacy_policy"
    const val SETTINGS_CLICK_RATE_US = "settings_click_rate_us"
    const val SETTINGS_CLICK_INVITE_FRIENDS = "settings_click_invite_friends"

    //Chat click events
    const val CHAT_CLICK_SEND = "chat_click_send"
    const val CHAT_CLICK_ATTACHMENT = "chat_click_attachment"
    const val CHAT_CLICK_MENU = "chat_click_menu"
    const val CHAT_CLICK_SCHEDULE = "chat_click_schedule"
    const val CHAT_CLICK_BUBBLE_REPLY = "chat_click_bubble_reply"
    const val CHAT_CLICK_MESSAGE_INFO = "chat_click_message_info"

    //Schedule click events
    const val SCHEDULE_CLICK_SET_SCHEDULE = "schedule_click_set_schedule"
    const val SCHEDULE_CLICK_CANCEL = "schedule_click_cancel"

    //Select contact click events
    const val SELECT_CONTACT_CLICK_SEARCH = "select_contact_click_search"
    const val SELECT_CONTACT_CLICK_CONTACT = "select_contact_click_contact"

    //Common events
    const val CLICK_BACK = "click_back"
    const val CLICK_DONE = "click_done"

}
