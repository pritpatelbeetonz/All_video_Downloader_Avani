package app.messages.data.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exact alarms survive neither a reboot NOR an app update, so on BOOT_COMPLETED
 * and MY_PACKAGE_REPLACED we re-arm every pending scheduled message. Anything
 * whose time already elapsed is sent immediately rather than dropped.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun dao(): app.messages.data.db.ScheduledMessageDao
        fun scheduler(): ScheduledMessageScheduler
        fun executor(): ScheduledSendExecutor
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A reboot AND an app update both clear our exact alarms — re-arm on
        // either, or scheduled sends silently stop firing after an update.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootReceiverEntryPoint::class.java
        )
        val dao = entryPoint.dao()
        val scheduler = entryPoint.scheduler()
        val executor = entryPoint.executor()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                dao.listByStatus(ScheduledStatus.PENDING.name).forEach { row ->
                    if (row.scheduledAtMs <= now) {
                        executor.send(row)
                    } else {
                        scheduler.schedule(row.id, row.scheduledAtMs)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
