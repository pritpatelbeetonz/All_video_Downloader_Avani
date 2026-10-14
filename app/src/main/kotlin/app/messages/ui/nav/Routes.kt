package app.messages.ui.nav

/**
 * String routes for [androidx.navigation.compose.NavHost]. Type-safety here
 * is light because the route set is tiny; M2 onward we'll switch to typed
 * routes once we have arguments richer than a single thread id.
 */
object Routes {
    const val Onboarding = "onboarding"
    const val Conversations = "conversations"
    const val Thread = "thread/{threadId}"
    const val Compose = "compose"
    const val Search = "search"
    const val Settings = "settings"
    const val Premium = "premium"
    const val Archived = "archived"
    const val Blocked = "blocked"
    const val Starred = "starred"
    const val Scheduled = "scheduled"
    const val AppLock = "applock"
    const val Backup = "backup"
    const val NotificationSettings = "notification_settings"
    const val Privacy = "privacy"

    const val ThreadArg = "threadId"

    fun thread(threadId: String): String = "thread/$threadId"
}
