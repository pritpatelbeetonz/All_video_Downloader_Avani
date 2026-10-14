package app.messages.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Notification channels.
 *
 * - [MESSAGES]: high-importance heads-up for incoming SMS/MMS + inline reply.
 * - [SCHEDULED]: default-importance confirmations after a scheduled send fires
 *   (M5) — kept separate so the user can silence confirmations without losing
 *   incoming-message alerts.
 *
 * Per build doc §F7 the eventual design is per-conversation channels with custom
 * sound (custom sound = Premium, §6.3). That layers on without changing notify.
 */
object NotificationChannels {
    const val MESSAGES = "messages"
    const val SCHEDULED = "scheduled"

    fun ensure(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        if (manager.getNotificationChannel(MESSAGES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming text messages"
                    enableVibration(true)
                    setShowBadge(true)
                },
            )
        }

        if (manager.getNotificationChannel(SCHEDULED) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SCHEDULED,
                    "Scheduled messages",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Confirmations when a scheduled message is sent"
                    setShowBadge(false)
                },
            )
        }
    }
}
