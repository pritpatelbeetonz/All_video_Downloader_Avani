package app.messages.data.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [AlarmManager] for scheduled sends (§F13, §7.3). Each queued
 * row gets one exact alarm keyed on its row id; on fire the [ScheduledSendReceiver]
 * performs the send even if no UI process is alive.
 *
 * Exact alarms need `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (declared in the
 * manifest). If exact is somehow not permitted we degrade to an inexact alarm
 * rather than silently dropping the message.
 */
@Singleton
class ScheduledMessageScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /** True when the OS will honour an exact alarm for us right now. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(id: Long, atMs: Long) {
        val pi = pendingIntent(id)
        val am = alarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    fun cancel(id: Long) {
        alarmManager.cancel(pendingIntent(id))
    }

    private fun pendingIntent(id: Long): PendingIntent {
        val intent = Intent(context, ScheduledSendReceiver::class.java).apply {
            action = ScheduledSendReceiver.ACTION_SEND
            setPackage(context.packageName)
            putExtra(ScheduledSendReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
