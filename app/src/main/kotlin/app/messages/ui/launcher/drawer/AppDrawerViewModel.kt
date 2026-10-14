package app.messages.ui.launcher.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.model.AppCategory
import app.messages.data.model.AppInfo
import app.messages.domain.usecase.GetAppsByCategoryUseCase
import app.messages.domain.usecase.GetRecentAppsUseCase
import app.messages.domain.usecase.GetInstalledAppsUseCase
import app.messages.domain.usecase.LaunchAppUseCase
import app.messages.domain.usecase.ObserveHasFavoritesUseCase
import app.messages.domain.usecase.OpenAppInfoUseCase
import app.messages.domain.usecase.SearchAppsUseCase
import app.messages.domain.usecase.ToggleFavoriteUseCase
import app.messages.domain.usecase.UninstallAppUseCase
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import app.messages.data.repository.AppRepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import app.messages.data.repository.AppRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AppDrawerViewModel constructor(
    private val searchApps: SearchAppsUseCase,
    private val getAppsByCategory: GetAppsByCategoryUseCase,
    private val launchApp: LaunchAppUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val openAppInfo: OpenAppInfoUseCase,
    private val uninstallApp: UninstallAppUseCase,
    private val getInstalledApps: GetInstalledAppsUseCase,
    observeHasFavorites: ObserveHasFavoritesUseCase,
    getRecentApps: GetRecentAppsUseCase,
    private val repository: AppRepository,
    /** Our own package, hidden from the drawer — you don't launch the launcher. */
    private val ownPackageName: String = "",
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = AppRepositoryProvider.get(context)
            return AppDrawerViewModel(
                SearchAppsUseCase(repo),
                GetAppsByCategoryUseCase(repo),
                LaunchAppUseCase(repo),
                ToggleFavoriteUseCase(repo),
                OpenAppInfoUseCase(repo),
                UninstallAppUseCase(repo),
                GetInstalledAppsUseCase(repo),
                ObserveHasFavoritesUseCase(repo),
                GetRecentAppsUseCase(repo),
                repo,
                context.packageName,
            ) as T
        }
    }

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(AppCategory.ALL)

    val recentApps: StateFlow<List<AppInfo>> = getRecentApps()
        .map { list -> list.filterNot { it.packageName == ownPackageName }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasFavorites: StateFlow<Boolean> = observeHasFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val selectedCategory: StateFlow<AppCategory> = category.asStateFlow()

    val apps: StateFlow<List<AppInfo>> = combine(query, category) { q, c -> q to c }
        .flatMapLatest { (q, c) ->
            if (q.isNotBlank()) {
                // Search across all installed apps on device
                searchApps(q)
            } else {
                getAppsByCategory(c)
            }
        }
        // The dock and the home grid still need our own entry, so it is filtered
        // here rather than dropped from the repository scan.
        .map { list -> list.filterNot { it.packageName == ownPackageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun onCategorySelected(category: AppCategory) {
        this.category.value = category
    }

    fun openApp(app: AppInfo) = launchApp(app)

    fun onToggleFavorite(app: AppInfo) {
        viewModelScope.launch { toggleFavorite(app) }
    }

    fun onOpenAppInfo(app: AppInfo) = openAppInfo(app)

    fun onUninstall(app: AppInfo): Boolean = uninstallApp(app)

    fun refreshApps() {
        viewModelScope.launch { 
            getInstalledApps.refresh()
            repository.refreshRecency()
        }
    }
}
