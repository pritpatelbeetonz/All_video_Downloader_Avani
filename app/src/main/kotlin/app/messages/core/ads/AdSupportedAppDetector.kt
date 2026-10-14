package app.messages.core.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides whether a launcher tap on an installed app is allowed to carry an ad.
 *
 * An app qualifies only when its own manifest declares the Google Mobile Ads
 * meta-data `com.google.android.gms.ads.APPLICATION_ID` under `<application>` —
 * i.e. the app itself monetizes with AdMob. Every other app launches instantly,
 * with no ad and without advancing the interstitial counter.
 *
 * The manifest is deliberately NOT read by unzipping the APK. PackageManager
 * already parsed that exact manifest at install time, so [PackageManager.GET_META_DATA]
 * returns the same meta-data for the cost of one binder call (~1ms) instead of
 * reading a multi-hundred-MB APK off disk and decoding binary XML — and it works
 * for every app the launcher can already see, with no storage permission.
 *
 * Results are memoised per package for the life of the process. Firmware and
 * Google apps never ship the meta-data, so they are short-circuited by
 * [isNeverAdSupported] before any binder call happens at all.
 */
object AdSupportedAppDetector {

    private const val ADMOB_APP_ID_META = "com.google.android.gms.ads.APPLICATION_ID"

    private const val TAG = "AdSupportedApps"

    /**
     * Firmware / vendor namespaces that never carry AdMob. Skipping them by name
     * is what keeps a full pass over a 150-app device cheap: roughly half of the
     * list is answered without touching PackageManager.
     */
    private val SKIPPED_PACKAGE_PREFIXES = arrayOf(
        "android.",
        "com.android.",
        "com.google.android.",
        "com.google.ar.",
        "com.samsung.android.",
        "com.sec.android.",
        "com.samsung.",
        "com.miui.",
        "com.xiaomi.",
        "com.mi.android.",
        "com.oppo.",
        "com.coloros.",
        "com.oplus.",
        "com.heytap.",
        "com.realme.",
        "com.vivo.",
        "com.bbk.",
        "com.iqoo.",
        "com.oneplus.",
        "com.motorola.",
        "com.lge.",
        "com.huawei.",
        "com.hihonor.",
        "com.transsion.",
        "com.infinix.",
        "com.tecno.",
        "com.itel.",
        "com.nothing.",
        "com.qualcomm.",
        "com.mediatek.",
        "com.qti.",
        "org.codeaurora.",
    )

    /**
     * Exact packages outside the prefixes above that are still guaranteed ad-free
     * from our point of view.
     */
    private val SKIPPED_PACKAGES = hashSetOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.packageinstaller",
    )

    /** packageName -> does this app declare the AdMob app-id meta-data. */
    private val cache = ConcurrentHashMap<String, Boolean>()

    private val prewarmRunning = AtomicBoolean(false)

    private val prewarmExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ad-supported-scan").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }

    /**
     * True when a tap on [packageName] may show an ad.
     *
     * Answers from the in-memory cache when [prewarm] has already covered the
     * package (the normal case, since the launcher scans on every app-list
     * refresh); otherwise it resolves inline, which is a single binder call.
     */
    fun hasAds(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        cache[packageName]?.let { return it }
        val resolved = resolve(context.applicationContext.packageManager, packageName)
        cache[packageName] = resolved
        return resolved
    }

    /**
     * Resolves every package in [packageNames] on a background thread so the next
     * tap answers from memory. Safe to call on every app-list refresh: packages
     * already resolved are skipped, and overlapping calls are dropped.
     */
    fun prewarm(context: Context, packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val appContext = context.applicationContext
        val pending = packageNames.filterNot { it.isBlank() || cache.containsKey(it) }
        if (pending.isEmpty()) return
        if (!prewarmRunning.compareAndSet(false, true)) return

        prewarmExecutor.execute {
            try {
                val pm = appContext.packageManager
                pending.forEach { pkg ->
                    if (!cache.containsKey(pkg)) {
                        cache[pkg] = resolve(pm, pkg)
                    }
                    EssentialAppsDetector.isEssentialApp(appContext, pkg)
                }
                logSummary(packageNames)
            } finally {
                prewarmRunning.set(false)
            }
        }
    }

    /**
     * Drops the cached answer for [packageName] so the next lookup re-reads the
     * manifest. Call this when a package is installed, updated or removed — an
     * update can add or drop the AdMob meta-data.
     */
    fun invalidate(packageName: String) {
        if (packageName.isNotBlank()) cache.remove(packageName)
    }

    /** Number of currently known apps that declare the AdMob meta-data. */
    fun adSupportedCount(): Int = cache.count { it.value }

    private fun resolve(pm: PackageManager, packageName: String): Boolean {
        if (isNeverAdSupported(packageName)) return false
        val appInfo = try {
            pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            // Not installed any more, or not visible to us — treat as ad-free.
            return false
        }
        if (isFirmwareApp(appInfo)) return false
        // containsKey rather than getString: apps that point the meta-data at a
        // string resource (android:value="@string/admob_app_id") land in the
        // bundle as an int resource id, and they consume AdMob just the same.
        return appInfo.metaData?.containsKey(ADMOB_APP_ID_META) == true
    }

    private fun isNeverAdSupported(packageName: String): Boolean {
        if (packageName in SKIPPED_PACKAGES) return true
        return SKIPPED_PACKAGE_PREFIXES.any { packageName.startsWith(it) }
    }

    /**
     * Preinstalled firmware apps never carry AdMob. An updated system app is not
     * treated as firmware: it can be a store build (a preinstalled social app
     * updated from Play) that does monetize.
     */
    private fun isFirmwareApp(info: ApplicationInfo): Boolean {
        val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return isSystem && !isUpdatedSystem
    }

    private fun logSummary(scanned: Collection<String>) {
        val withAds = scanned.filter { cache[it] == true }
        android.util.Log.d(
            TAG,
            "scanned=${scanned.size} withAds=${withAds.size} withoutAds=${scanned.size - withAds.size}"
        )
        // The package list is the answer to "why did no app show an ad" — either the
        // device genuinely has few AdMob apps, or the scan is not seeing them.
        withAds.chunked(10).forEach { chunk ->
            android.util.Log.d(TAG, "withAds: ${chunk.joinToString()}")
        }
    }
}
