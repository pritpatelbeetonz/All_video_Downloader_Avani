package app.messages.domain.usecase

import app.messages.data.model.AppCategory
import app.messages.data.model.AppInfo
import app.messages.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow


class GetInstalledAppsUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.apps
    suspend fun refresh() = repository.refreshApps()
}

class SearchAppsUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(query: String): Flow<List<AppInfo>> = repository.searchApps(query)
}

class GetAppsByCategoryUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(category: AppCategory): Flow<List<AppInfo>> = repository.appsByCategory(category)
}

class LaunchAppUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(app: AppInfo) = repository.launchApp(app)
}

class ToggleFavoriteUseCase constructor(
    private val repository: AppRepository
) {
    suspend operator fun invoke(app: AppInfo) = repository.toggleFavorite(app)
}

class OpenAppInfoUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(app: AppInfo) = repository.openAppInfo(app)
}

class UninstallAppUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(app: AppInfo): Boolean = repository.uninstallApp(app)
}

class ObserveHasFavoritesUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.hasFavorites
}

class GetDockAppsUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.getDockApps()
}

class GetSuggestedAppsUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.getSuggestedApps()
}

class GetRecentAppsUseCase constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.getRecentApps()
}
