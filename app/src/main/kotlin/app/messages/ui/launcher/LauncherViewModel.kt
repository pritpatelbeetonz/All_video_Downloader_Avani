package app.messages.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.model.AppInfo
import app.messages.domain.usecase.GetDockAppsUseCase
import app.messages.domain.usecase.GetInstalledAppsUseCase
import app.messages.domain.usecase.LaunchAppUseCase
import app.messages.ui.launcher.adapter.LauncherPagerAdapter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import app.messages.data.repository.AppRepositoryProvider

class LauncherViewModel constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val getDockApps: GetDockAppsUseCase,
    private val launchApp: LaunchAppUseCase,
    private val context: Context? = null
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = AppRepositoryProvider.get(context)
            return LauncherViewModel(
                GetInstalledAppsUseCase(repo),
                GetDockAppsUseCase(repo),
                LaunchAppUseCase(repo),
                context.applicationContext
            ) as T
        }
    }

    private val _currentPage = MutableStateFlow(LauncherPagerAdapter.PAGE_HOME)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _openDrawerEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openDrawerEvents: SharedFlow<Unit> = _openDrawerEvents.asSharedFlow()

    private val _closeDrawerEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeDrawerEvents: SharedFlow<Unit> = _closeDrawerEvents.asSharedFlow()

    private val _focusDrawerSearchEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusDrawerSearchEvents: SharedFlow<Unit> = _focusDrawerSearchEvents.asSharedFlow()

    private val _navigateToChatsEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToChatsEvents: SharedFlow<Unit> = _navigateToChatsEvents.asSharedFlow()

    private var openDrawerWithSearch = false

    val dockApps: StateFlow<List<AppInfo>> = getDockApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch { getInstalledApps.refresh() }
    }

    fun setPage(page: Int) {
        _currentPage.value = page
    }

    fun openApp(app: AppInfo) {
        if (context != null && app.packageName == context.packageName) {
            requestNavigateToChats()
        } else {
            launchApp(app)
        }
    }

    fun requestNavigateToChats() {
        requestCloseDrawer()
        _navigateToChatsEvents.tryEmit(Unit)
    }

    fun requestOpenDrawer() {
        openDrawerWithSearch = false
        _openDrawerEvents.tryEmit(Unit)
    }

    fun requestOpenDrawerWithSearch() {
        if (_drawerOpen.value) {
            openDrawerWithSearch = false
            _focusDrawerSearchEvents.tryEmit(Unit)
            return
        }
        openDrawerWithSearch = true
        _openDrawerEvents.tryEmit(Unit)
    }

    fun requestCloseDrawer() {
        openDrawerWithSearch = false
        _closeDrawerEvents.tryEmit(Unit)
    }

    fun setDrawerOpenState(open: Boolean) {
        _drawerOpen.value = open
        if (open && openDrawerWithSearch) {
            openDrawerWithSearch = false
            _focusDrawerSearchEvents.tryEmit(Unit)
        }
        if (!open) {
            openDrawerWithSearch = false
        }
    }
}
