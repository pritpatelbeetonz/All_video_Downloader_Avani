package app.messages.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit



data class AppUsageSummary(
    val packageName: String,
    val totalTimeMs: Long,
    val lastUsedMs: Long
)

data class TodayUsageSnapshot(
    val totalScreenTimeMs: Long,
    val topApps: List<AppUsageSummary>
)


class UsageStatsRepository constructor(
    private val context: Context
) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun getRecentPackages(limit: Int = 8): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext emptyList()
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(2)
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val lastUsed = linkedMapOf<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isMoveToFg = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isMoveToFg && !event.packageName.isNullOrBlank()) {
                if (event.packageName == context.packageName) continue
                lastUsed[event.packageName] = event.timeStamp
            }
        }
        lastUsed.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
            .distinctBy { it.first }
            .take(limit)
    }

    suspend fun getTopUsedPackages(
        days: Int = 7,
        limit: Int = 8
    ): List<AppUsageSummary> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return@withContext emptyList()
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(days.toLong())
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .groupBy { it.packageName }
            .mapNotNull { (pkg, stats) ->
                if (pkg.isNullOrBlank() || pkg == context.packageName) return@mapNotNull null
                val total = stats.sumOf { it.totalTimeInForeground }
                val last = stats.maxOf { it.lastTimeUsed }
                if (total <= 0L) return@mapNotNull null
                AppUsageSummary(pkg, total, last)
            }
            .sortedByDescending { it.totalTimeMs }
            .take(limit)
    }

    suspend fun getTodayUsageSnapshot(limit: Int = 3): TodayUsageSnapshot = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext TodayUsageSnapshot(0L, emptyList())
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext TodayUsageSnapshot(0L, emptyList())
        val end = System.currentTimeMillis()
        val start = startOfTodayMs()
        val tops = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .orEmpty()
            .groupBy { it.packageName }
            .mapNotNull { (pkg, stats) ->
                if (pkg.isNullOrBlank() || pkg == context.packageName) return@mapNotNull null
                val total = stats.sumOf { it.totalTimeInForeground }
                if (total <= 0L) return@mapNotNull null
                AppUsageSummary(pkg, total, stats.maxOf { it.lastTimeUsed })
            }
            .sortedByDescending { it.totalTimeMs }
        TodayUsageSnapshot(
            totalScreenTimeMs = tops.sumOf { it.totalTimeMs },
            topApps = tops.take(limit)
        )
    }

    private fun startOfTodayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
