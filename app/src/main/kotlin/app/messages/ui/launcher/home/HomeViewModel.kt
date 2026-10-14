package app.messages.ui.launcher.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.model.AppInfo
import app.messages.domain.usecase.GetDockAppsUseCase
import app.messages.domain.usecase.GetInstalledAppsUseCase
import app.messages.domain.usecase.LaunchAppUseCase
import app.messages.domain.usecase.OpenAppInfoUseCase
import app.messages.domain.usecase.ToggleFavoriteUseCase
// Removed Hilt import
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import app.messages.data.repository.AppRepositoryProvider

class HomeViewModel constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val getDockApps: GetDockAppsUseCase,
    private val launchApp: LaunchAppUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val openAppInfo: OpenAppInfoUseCase
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = AppRepositoryProvider.get(context)
            return HomeViewModel(
                GetInstalledAppsUseCase(repo),
                GetDockAppsUseCase(repo),
                LaunchAppUseCase(repo),
                ToggleFavoriteUseCase(repo),
                OpenAppInfoUseCase(repo)
            ) as T
        }
    }

    val workspaceApps: StateFlow<List<AppInfo>> = combine(getInstalledApps(), getDockApps()) { installed, dock ->
        val dockPackages = dock.map { it.packageName }.toSet()
        pickWorkspace(installed, dockPackages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dockApps: StateFlow<List<AppInfo>> = getDockApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { getInstalledApps.refresh() }
    }

    fun openApp(app: AppInfo) = launchApp(app)

    fun onToggleFavorite(app: AppInfo) {
        viewModelScope.launch { toggleFavorite(app) }
    }

    fun onOpenAppInfo(app: AppInfo) = openAppInfo(app)

    fun refreshApps() {
        viewModelScope.launch { getInstalledApps.refresh() }
    }

    fun currentTime(): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())

    fun currentDate(): String =
        SimpleDateFormat("EEE dd MMMM", Locale.getDefault()).format(Date())

    private fun pickWorkspace(apps: List<AppInfo>, dockPackages: Set<String>): List<AppInfo> {
        val mainAppKeys = listOf(
            "whatsapp", "instagram", "facebook", "telegram", "messenger", "youtube",
            "gmail", "photos", "gallery", "camera", "phone", "chrome",
            "contacts", "settings", "play store", "vending", "calendar",
            "drive", "maps", "clock", "calculator"
        )
        val picked = mutableListOf<AppInfo>()
        // Initialize used with all packages already in the bottom dock to prevent duplicates
        val used = dockPackages.toMutableSet()

        mainAppKeys.forEach { key ->
            val match = apps.firstOrNull { app ->
                app.packageName !in used && (
                    app.label.contains(key, ignoreCase = true) ||
                    app.packageName.contains(key, ignoreCase = true)
                )
            }
            if (match != null) {
                picked.add(match)
                used.add(match.packageName)
            }
        }

        val favorites = apps.filter { it.isFavorite && it.packageName !in used }
        favorites.forEach {
            picked.add(it)
            used.add(it.packageName)
        }

        val remaining = apps.filter { it.packageName !in used }
        return (picked + remaining).distinctBy { it.packageName }.take(5)
    }
}
