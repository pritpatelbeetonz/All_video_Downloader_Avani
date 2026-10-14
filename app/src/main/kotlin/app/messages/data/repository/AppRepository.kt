package app.messages.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import app.messages.data.model.AppCategory
import app.messages.data.model.AppInfo
import app.messages.data.repository.UsageStatsRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext



/**
 * Process-wide holder for the app list.
 *
 * The launcher's four ViewModels (Launcher / Home / Dashboard / AppDrawer) used to
 * build one [AppRepositoryImpl] each, so each of them ran its own full
 * PackageManager scan on init — four concurrent passes over every installed app,
 * none of them sharing a result. Sharing one instance means the scan runs once and
 * every screen reads the same [StateFlow].
 *
 */
object AppRepositoryProvider {
    @Volatile
    private var instance: AppRepositoryImpl? = null

    fun get(context: Context): AppRepositoryImpl {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: AppRepositoryImpl(
                context.applicationContext,
                UsageStatsRepository(context.applicationContext),
            ).also { instance = it }
        }
    }
}

interface AppRepository {
    val apps: Flow<List<AppInfo>>
    val hasFavorites: Flow<Boolean>
    suspend fun refreshApps()
    fun searchApps(query: String): Flow<List<AppInfo>>
    fun appsByCategory(category: AppCategory): Flow<List<AppInfo>>
    fun launchApp(app: AppInfo)
    fun openAppInfo(app: AppInfo)
    fun uninstallApp(app: AppInfo): Boolean
    suspend fun toggleFavorite(app: AppInfo)
    fun getDockApps(): Flow<List<AppInfo>>
    fun getSuggestedApps(): Flow<List<AppInfo>>
    fun getRecentApps(): Flow<List<AppInfo>>
    fun refreshRecency()
}


@OptIn(ExperimentalCoroutinesApi::class)
class AppRepositoryImpl constructor(
    private val context: Context,
    private val usageStatsRepository: UsageStatsRepository
) : AppRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val favoritePackages = MutableStateFlow(loadFavoritePackages())
    private val recencyEpoch = MutableStateFlow(0L)

    private val refreshMutex = Mutex()
    private var lastScanAt = 0L

    override val apps: Flow<List<AppInfo>> = combine(_apps, favoritePackages) { list, favorites ->
        val mapped = list.map { app -> app.copy(isFavorite = app.packageName in favorites) }
        app.messages.ui.launcher.drawer.GlobalAppCache.lastApps = mapped
        mapped
    }

    override val hasFavorites: Flow<Boolean> = favoritePackages.map { it.isNotEmpty() }

    override suspend fun refreshApps() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // Home, Dashboard, AppDrawer and the host all ask for a refresh within a
            // few hundred ms of launcher startup. Serve the burst from the scan that
            // just ran instead of walking every installed app again per caller.
            val sinceLastScan = android.os.SystemClock.elapsedRealtime() - lastScanAt
            if (_apps.value.isNotEmpty() && sinceLastScan < SCAN_COALESCE_MS) {
                return@withLock
            }
            scanInstalledApps()
            lastScanAt = android.os.SystemClock.elapsedRealtime()
        }
    }

    private fun scanInstalledApps() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val favorites = favoritePackages.value
        val mapped = resolveInfos
            .asSequence()
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                // queryIntentActivities already populates applicationInfo, so read the
                // flags/category off it rather than paying two more getApplicationInfo
                // binder round-trips per app.
                val appInfo: ApplicationInfo? = activityInfo.applicationInfo
                AppInfo(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = label,
                    icon = null, // Defer icon loading to the RecyclerView Adapter for instant startup
                    category = categorize(appInfo, activityInfo.packageName, label),
                    isFavorite = activityInfo.packageName in favorites,
                    canUninstall = canUninstall(appInfo, activityInfo.packageName)
                )
            }
            .distinctBy { it.packageName + it.activityName }
            .toMutableList()

        if (mapped.none { it.packageName == context.packageName }) {
            val appLabel = try {
                context.packageManager.getApplicationLabel(context.applicationInfo).toString()
            } catch (_: Exception) {
                context.getString(app.messages.R.string.app_name)
            }
            mapped.add(
                AppInfo(
                    packageName = context.packageName,
                    activityName = app.messages.ui.activity.SplashActivity::class.java.name,
                    label = appLabel,
                    icon = null,
                    category = AppCategory.VIDEO,
                    isFavorite = false,
                    canUninstall = false
                )
            )
        }

        val sorted = mapped.sortedBy { it.label.lowercase() }
        _apps.value = sorted
        pruneMissingFavorites(sorted.map { it.packageName }.toSet())
        pruneMissingLaunchRecents(sorted.map { it.packageName }.toSet())
        // Work out which of these apps monetize with AdMob while the user is still
        // looking at the freshly emitted list, so the first tap answers from memory.
        app.messages.core.ads.AdSupportedAppDetector.prewarm(
            context,
            sorted.map { it.packageName }.distinct()
        )
    }

    override fun searchApps(query: String): Flow<List<AppInfo>> =
        apps.map { list ->
            if (query.isBlank()) list
            else {
                // Normalize: trim edges and collapse multiple spaces into one
                val normalized = query.trim().replace(Regex("\\s+"), " ").lowercase()
                list.filter { app ->
                    app.label.trim().lowercase().contains(normalized)
                }
            }
        }

    override fun appsByCategory(category: AppCategory): Flow<List<AppInfo>> =
        apps.map { list ->
            when (category) {
                AppCategory.ALL -> list
                AppCategory.FAVORITES -> list.filter { it.isFavorite }
                else -> list.filter { it.category == category }
            }
        }

    override fun refreshRecency() {
        recencyEpoch.value = System.currentTimeMillis()
    }

    override fun launchApp(app: AppInfo) {
        recordLaunch(app.packageName)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun uninstallApp(appInfo: AppInfo): Boolean {
        if (!appInfo.canUninstall) {
            val info = runCatching {
                context.packageManager.getApplicationInfo(appInfo.packageName, 0)
            }.getOrNull()
            if (!canUninstall(info, appInfo.packageName)) return false
        }
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${appInfo.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.messages.ui.launcher.drawer.AppActionCache.setPendingUninstall(
                appInfo.packageName,
                appInfo.label,
                appInfo.icon
            )
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun toggleFavorite(app: AppInfo) = withContext(Dispatchers.IO) {
        val updated = favoritePackages.value.toMutableSet()
        if (app.packageName in updated) updated.remove(app.packageName)
        else updated.add(app.packageName)
        favoritePackages.value = updated
        prefs.edit().putStringSet(KEY_FAVORITES, HashSet(updated)).apply()
    }

    override fun getDockApps(): Flow<List<AppInfo>> =
        apps.map { list ->
            val defaultSmsPackage = runCatching {
                android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            }.getOrNull()?.takeIf { it != context.packageName }

            // 5 Dock apps: Phone (1st) → Default Messages → Chrome → Photos/Gallery (4th) → Camera (5th)
            val slots = listOf(
                // 1. Phone
                listOf(
                    "com.google.android.dialer",
                    "com.samsung.android.dialer",
                    "com.android.dialer",
                    "com.android.phone"
                ),
                // 2. Default Phone Messages (OEM / System SMS)
                listOfNotNull(
                    defaultSmsPackage,
                    "com.samsung.android.messaging",
                    "com.google.android.apps.messaging",
                    "com.android.mms",
                    "com.oppo.mms",
                    "com.coloros.mms",
                    "com.oneplus.mms",
                    "com.vivo.mms",
                    "com.huawei.message"
                ),
                // 3. Chrome
                listOf(
                    "com.android.chrome",
                    "com.chrome.beta",
                    "com.chrome.dev",
                    "com.google.android.apps.chrome"
                ),
                // 4. Photos or Gallery (Device Default OEM Gallery first)
                listOf(
                    "com.coloros.gallery3d",
                    "com.coloros.photos",
                    "com.heytap.gallery",
                    "com.oppo.gallery3d",
                    "com.oplus.gallery",
                    "com.oneplus.gallery",
                    "com.miui.gallery",
                    "com.sec.android.gallery3d",
                    "com.vivo.gallery",
                    "com.huawei.photos",
                    "com.motorola.gallery",
                    "com.motorola.cn.gallery",
                    "com.android.gallery3d",
                    "com.android.gallery",
                    "com.google.android.apps.photosgo",
                    "com.google.android.apps.photos"
                ),
                // 5. Camera (Last)
                listOf(
                    "com.sec.android.app.camera",
                    "com.samsung.android.camera",
                    "com.oplus.camera",
                    "com.coloros.camera",
                    "com.oppo.camera",
                    "com.oneplus.camera",
                    "com.miui.camera",
                    "com.android.camera",
                    "com.android.camera2",
                    "com.google.android.GoogleCamera",
                    "com.huawei.camera",
                    "org.codeaurora.snapcam"
                )
            )
            val labelFallbacks = listOf(
                listOf("phone", "dialer", "call"),
                listOf("message", "messages", "chat", "sms"),
                listOf("chrome", "browser"),
                listOf("photos", "gallery", "photo"),
                listOf("camera", "cam")
            )
            val picked = mutableListOf<AppInfo>()
            val used = mutableSetOf<String>()

            slots.forEachIndexed { index, packages ->
                val byPackage = packages.firstNotNullOfOrNull { pkg ->
                    list.firstOrNull { it.packageName.equals(pkg, true) && it.packageName != context.packageName }
                }
                val byLabel = list.firstOrNull { app ->
                    app.packageName !in used &&
                        app.packageName != context.packageName &&
                        labelFallbacks[index].any { labelKey -> app.label.contains(labelKey, ignoreCase = true) }
                }
                val match = byPackage ?: byLabel
                if (match != null && match.packageName !in used) {
                    picked += match
                    used += match.packageName
                }
            }

            if (picked.size >= 5) {
                picked.take(5)
            } else {
                (picked + list.filter { it.packageName !in used && it.packageName != context.packageName }).distinctBy { it.packageName }.take(5)
            }
        }

    override fun getSuggestedApps(): Flow<List<AppInfo>> =
        combine(apps, recencyEpoch) { list, _ -> list }
            .flatMapLatest { list -> flow { emit(buildSuggestedApps(list)) } }

    override fun getRecentApps(): Flow<List<AppInfo>> =
        combine(apps, recencyEpoch) { list, _ -> list }
            .flatMapLatest { list -> flow { emit(buildRecentApps(list)) } }

    /**
     * Last-opened apps (recency). Prefers launches from this launcher, then usage
     * stats. Falls back to favorites and popular apps when recency has fewer items.
     */
    private suspend fun buildRecentApps(list: List<AppInfo>): List<AppInfo> {
        if (list.isEmpty()) return emptyList()
        val byPackage = launcherActivityByPackage(list)
        val result = mutableListOf<AppInfo>()
        val usedPackages = mutableSetOf<String>()

        // 1. Add apps from actual recent launches
        mergedRecentPackages()
            .mapNotNull { byPackage[it] }
            .forEach { app ->
                if (usedPackages.add(app.packageName)) {
                    result.add(app)
                }
            }

        // 2. If fewer than 10, fill from favorites
        if (result.size < 10) {
            list.filter { it.isFavorite }.forEach { app ->
                if (usedPackages.add(app.packageName)) {
                    result.add(app)
                }
            }
        }

        // 3. If still fewer than 5, fill from common apps (Google, WhatsApp, Gallery, etc.)
        if (result.size < 5) {
            val commonKeywords = listOf("whatsapp", "chrome", "youtube", "camera", "gallery", "photos", "gmail", "maps", "contact")
            commonKeywords.forEach { keyword ->
                val match = list.firstOrNull { it.packageName !in usedPackages && (it.label.contains(keyword, true) || it.packageName.contains(keyword, true)) }
                if (match != null && usedPackages.add(match.packageName)) {
                    result.add(match)
                }
            }
        }

        // 4. If still fewer than 5, fill from top installed apps
        if (result.size < 5) {
            list.forEach { app ->
                if (result.size < 5 && usedPackages.add(app.packageName)) {
                    result.add(app)
                }
            }
        }

        return result.take(10)
    }

    /**
     * Frequently used apps that are NOT already shown under Recent,
     * so the two grids stay visually different.
     */
    private suspend fun buildSuggestedApps(list: List<AppInfo>): List<AppInfo> {
        if (list.isEmpty()) return emptyList()

        val usefulKeys = listOf(
            "gmail", "google", "chrome", "youtube", "whatsapp",
            "maps", "photos", "gallery", "drive", "camera"
        )
        val picked = mutableListOf<AppInfo>()
        val used = mutableSetOf<String>()

        usefulKeys.forEach { key ->
            val match = list.firstOrNull { app ->
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

        val remaining = list.filter { it.packageName !in used }
        return (picked + remaining).distinctBy { it.packageName }.take(4)
    }

    private fun canUninstall(info: ApplicationInfo?, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (info == null) return false
        val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        // User-installed apps can be uninstalled. Updated system apps can remove updates.
        return !isSystem || isUpdatedSystem
    }

    private fun launcherActivityByPackage(list: List<AppInfo>): Map<String, AppInfo> {
        val grouped = list.groupBy { it.packageName }
        return grouped.mapValues { (pkg, activities) ->
            val launchClass = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)?.component?.className
            }.getOrNull()
            activities.firstOrNull { it.activityName == launchClass } ?: activities.first()
        }
    }

    private fun recordLaunch(packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        val times = loadLaunchRecentsTimed().toMutableMap()
        times[packageName] = System.currentTimeMillis()
        val ordered = times.entries.sortedByDescending { it.value }.take(LAUNCH_RECENTS_LIMIT)
        prefs.edit()
            .putString(
                KEY_LAUNCH_RECENTS,
                ordered.joinToString(RECENTS_SEPARATOR) { "${it.key}=${it.value}" }
            )
            .apply()
        recencyEpoch.value = System.currentTimeMillis()
    }

    private suspend fun mergedRecentPackages(): List<String> {
        val times = loadLaunchRecentsTimed().toMutableMap()
        usageStatsRepository.getRecentPackages(limit = 16).forEach { (pkg, time) ->
            times[pkg] = maxOf(times[pkg] ?: 0L, time)
        }
        return times.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it != context.packageName }
    }

    private fun loadLaunchRecentsTimed(): Map<String, Long> {
        val raw = prefs.getString(KEY_LAUNCH_RECENTS, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val now = System.currentTimeMillis()
        val result = LinkedHashMap<String, Long>()
        raw.split(RECENTS_SEPARATOR).filter { it.isNotBlank() }.forEachIndexed { index, token ->
            val parts = token.split('=', limit = 2)
            val pkg = parts[0]
            if (pkg.isBlank()) return@forEachIndexed
            val time = parts.getOrNull(1)?.toLongOrNull() ?: (now - index)
            result[pkg] = time
        }
        return result
    }

    private fun loadFavoritePackages(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    private fun pruneMissingFavorites(installedPackages: Set<String>) {
        val current = favoritePackages.value
        val pruned = current.filter { it in installedPackages }.toSet()
        if (pruned != current) {
            favoritePackages.value = pruned
            prefs.edit().putStringSet(KEY_FAVORITES, HashSet(pruned)).apply()
        }
    }

    private fun pruneMissingLaunchRecents(installedPackages: Set<String>) {
        val current = loadLaunchRecentsTimed()
        val pruned = current.filterKeys { it in installedPackages }
        if (pruned.size == current.size) return
        val ordered = pruned.entries.sortedByDescending { it.value }.take(LAUNCH_RECENTS_LIMIT)
        prefs.edit()
            .putString(
                KEY_LAUNCH_RECENTS,
                ordered.joinToString(RECENTS_SEPARATOR) { "${it.key}=${it.value}" }
            )
            .apply()
    }

    private fun categorize(appInfo: ApplicationInfo?, packageName: String, label: String): AppCategory {
        // ApplicationInfo.category is API 26+; below that fall straight to keywords.
        if (appInfo == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return categorizeByKeywords(packageName, label)
        }
        return when (appInfo.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.PHOTOGRAPHY
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> categorizeByKeywords(packageName, label)
        }
    }

    private fun categorizeByKeywords(packageName: String, label: String): AppCategory {
        val lower = "$packageName $label".lowercase()
        return when {
            listOf(
                "music", "spotify", "audio", "sound", "youtube music", "gaana", "wynk",
                "podcast", "radio", "shazam", "tidal", "deezer"
            ).any { lower.contains(it) } -> AppCategory.AUDIO

            listOf(
                "game", "pubg", "cod", "freefire", "roblox", "minecraft", "clash",
                "candy crush", "among us", "fortnite", "steam"
            ).any { lower.contains(it) } -> AppCategory.GAMES

            listOf(
                "camera", "gallery", "photo", "picsart", "snapseed", "lightroom",
                "photoshop", "vsco", "gcam", "imaging"
            ).any { lower.contains(it) } -> AppCategory.PHOTOGRAPHY

            listOf(
                "office", "docs", "sheets", "slides", "word", "excel", "powerpoint",
                "notion", "evernote", "onenote", "calendar", "drive", "dropbox",
                "notes", "todo", "task", "mail", "gmail", "outlook", "slack",
                "teams", "zoom", "meet", "pdf", "scanner", "keep"
            ).any { lower.contains(it) } -> AppCategory.PRODUCTIVITY

            listOf(
                "whatsapp", "instagram", "facebook", "messenger", "telegram", "twitter",
                "x.com", "snapchat", "linkedin", "discord", "reddit", "tiktok",
                "signal", "imo", "sharechat", "threads", "social"
            ).any { lower.contains(it) } -> AppCategory.SOCIAL

            listOf(
                "youtube", "netflix", "hotstar", "prime video", "disney", "video",
                "mx player", "vlc", "player", "twitch", "jiocinema", "sony liv",
                "shorts", "reels"
            ).any { lower.contains(it) } -> AppCategory.VIDEO

            else -> AppCategory.OTHERS
        }
    }

    private companion object {
        const val PREFS_NAME = "call_launcher_prefs"
        const val KEY_FAVORITES = "favorite_packages"
        const val KEY_LAUNCH_RECENTS = "launch_recents"
        const val RECENTS_SEPARATOR = "\u001f"
        const val LAUNCH_RECENTS_LIMIT = 16

        /**
         * Window in which a repeat [refreshApps] reuses the previous scan. Long
         * enough to swallow the startup burst from the launcher's ViewModels, short
         * enough that a package install/removal (always well past this) still scans.
         */
        const val SCAN_COALESCE_MS = 1_500L
    }
}
