package app.messages.ui.launcher.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.messages.data.model.AppInfo
import app.messages.data.repository.AppRepositoryImpl
import app.messages.data.repository.AppRepositoryProvider
import app.messages.data.repository.UsageStatsRepository
import app.messages.domain.usecase.GetRecentAppsUseCase
import app.messages.domain.usecase.GetSuggestedAppsUseCase
import app.messages.domain.usecase.LaunchAppUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class UsageCardState(
    val hasPermission: Boolean = false,
    val totalLabel: String = "",
    val topLines: List<String> = emptyList()
)

data class StorageInfoState(
    val usedPercentage: Int = 0,
    val usedGbLabel: String = "0 GB",
    val totalGbLabel: String = "0 GB",
    val freeGbLabel: String = "0 GB"
)

class DashboardViewModel(
    getSuggestedApps: GetSuggestedAppsUseCase,
    getRecentApps: GetRecentAppsUseCase,
    private val launchApp: LaunchAppUseCase,
    private val usageStatsRepository: UsageStatsRepository,
    private val appRepository: AppRepositoryImpl
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val usageStats = UsageStatsRepository(context.applicationContext)
            val repo = AppRepositoryProvider.get(context)
            return DashboardViewModel(
                GetSuggestedAppsUseCase(repo),
                GetRecentAppsUseCase(repo),
                LaunchAppUseCase(repo),
                usageStats,
                repo
            ) as T
        }
    }

    val suggestedApps: StateFlow<List<AppInfo>> = getSuggestedApps()
        .map { it.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentApps: StateFlow<List<AppInfo>> = getRecentApps()
        .map { it.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _usageCard = MutableStateFlow(UsageCardState())
    val usageCard: StateFlow<UsageCardState> = _usageCard.asStateFlow()

    private val _storageState = MutableStateFlow(StorageInfoState())
    val storageState: StateFlow<StorageInfoState> = _storageState.asStateFlow()

    init {
        viewModelScope.launch { appRepository.refreshApps() }
        refreshDashboard()
    }

    fun openApp(app: AppInfo) = launchApp(app)

    fun refreshDashboard() {
        appRepository.refreshRecency()
        refreshUsageCard()
        refreshStorageInfo()
    }

    fun refreshStorageInfo() {
        try {
            val stat = android.os.Environment.getDataDirectory()
            val totalBytes = stat.totalSpace
            val freeBytes = stat.freeSpace
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0

            _storageState.value = StorageInfoState(
                usedPercentage = percent,
                usedGbLabel = String.format(java.util.Locale.US, "%.1f GB", usedGb),
                totalGbLabel = String.format(java.util.Locale.US, "%.1f GB", totalGb),
                freeGbLabel = String.format(java.util.Locale.US, "%.1f GB Free", freeGb)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshUsageCard() {
        viewModelScope.launch {
            val hasPermission = usageStatsRepository.hasUsageAccess()
            if (!hasPermission) {
                _usageCard.value = UsageCardState(hasPermission = false)
                return@launch
            }
            val snapshot = usageStatsRepository.getTodayUsageSnapshot(limit = 3)
            val installed = appRepository.apps.first()
            val lines = if (snapshot.topApps.isEmpty()) {
                listOf("No app usage recorded today")
            } else {
                snapshot.topApps.map { summary ->
                    val label = installed.firstOrNull { it.packageName == summary.packageName }?.label
                        ?: summary.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    "$label · ${formatDuration(summary.totalTimeMs)}"
                }
            }
            _usageCard.value = UsageCardState(
                hasPermission = true,
                totalLabel = "Today · ${formatDuration(snapshot.totalScreenTimeMs)}",
                topLines = lines
            )
        }
    }

    fun openUsageAccessSettings() = usageStatsRepository.openUsageAccessSettings()

    private fun formatDuration(ms: Long): String {
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(ms).coerceAtLeast(0)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
}
