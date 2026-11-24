package app.messages.ads.ng

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Override for the native-ad MEDIA area.
 *
 * The real AdMob [MediaView] is SDK-rendered — you cannot push your own bitmap into it. So when the
 * override is on for a surface, the MediaView is removed and a plain [ImageView] showing a bundled
 * asset is dropped in its exact place (same parent, index and layout params). The rest of the native
 * ad (headline / body / icon / CTA) still binds from the REAL ad; only the media visual is replaced.
 *
 * Gating (all on [app.messages.config.AdsConfig]):
 *  - [nativeMediaOverrideOn] = master. OFF (default) -> real AdMob media everywhere.
 *  - per-surface booleans ([nativeMediaLanguage], [nativeMediaDrawer], ...) -> WHERE it applies.
 *  - [nativeMediaWebp] / [nativeMediaImage] = WHICH asset (WebP wins if both on; both off -> real media).
 *
 * WHERE the image comes from ([NativeMediaCatalog]):
 *  1. SERVER: {serverUrl}/{serverBaseFolderName}/nativeAd_media.json (Remote Config), each entry's
 *     `url` loaded with Coil (disk-cached; animated WebP plays via the app's ImageDecoderDecoder).
 *  2. BUNDLED: assets/native_media.json -> res/drawable. Used until the server feed has loaded, and
 *     as the error fallback when the server image fails to download.
 *  3. Neither -> the real AdMob media is kept.
 *
 * Logcat: tag:NativeMediaOverride / tag:NativeMediaCatalog
 */
object NativeMediaOverride {

    /** True when the media override should replace the real media on [surface]. */
    @JvmStatic
    fun isEnabledFor(surface: String): Boolean {
        val c = app.messages.config.AdsConfigManager.config
        if (!c.nativeMediaOverrideOn) return false
        return when (surface.trim().lowercase()) {
            "language" -> c.nativeMediaLanguage
            "settings" -> c.nativeMediaSettings
            "flowa" -> c.nativeMediaFlowa
            "dashboard" -> c.nativeMediaDashboard
            "search" -> c.nativeMediaSearch
            "action_success" -> c.nativeMediaActionSuccess
            "drawer" -> c.nativeMediaDrawer
            "downloader", "downloader_big" -> c.nativeMediaDownloader
            else -> false
        }
    }

    /**
     * Returns the [MediaView] to register with the ad, or `null` when it was swapped for the override
     * image. [mediaViewId] is the MediaView's id in the inflated layout (`ad_media` / `native_ad_media`).
     * If the override is on but the asset can't be resolved, the real MediaView is kept (returned).
     */
    @JvmStatic
    fun resolveMediaView(adView: NativeAdView, mediaViewId: Int, surface: String): MediaView? {
        val mediaView = adView.findViewById<MediaView>(mediaViewId)
        if (!isEnabledFor(surface)) return mediaView
        if (mediaView == null) return null

        // WHICH asset: WebP wins when both are on; both off → keep the real media even here.
        val cfg = app.messages.config.AdsConfigManager.config
        val type = when {
            cfg.nativeMediaWebp -> "webp"
            cfg.nativeMediaImage -> "image"
            else -> null
        } ?: return mediaView
        val serverUrl = NativeMediaCatalog.serverUrlFor(type, surface)
        val resId = NativeMediaCatalog.drawableFor(adView.context, type, surface)
        if (serverUrl == null && resId == 0) {
            Log.w("NativeMediaOverride", "no asset for type='$type' on surface='$surface' → keep real media")
            return mediaView
        }

        val parent = mediaView.parent as? ViewGroup ?: return mediaView
        val index = parent.indexOfChild(mediaView)
        val params = mediaView.layoutParams
        val image = ImageView(adView.context).apply {
            layoutParams = params
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (serverUrl != null) {
                Log.d("NativeMediaOverride", "surface='$surface' type='$type' → server $serverUrl")
                load(serverUrl) {
                    crossfade(true)
                    if (resId != 0) error(resId)   // server image failed → bundled one
                }
            } else {
                Log.d("NativeMediaOverride", "surface='$surface' type='$type' → bundled drawable (server feed not loaded)")
                setImageResource(resId)
            }
        }
        parent.removeView(mediaView)
        parent.addView(image, index)
        return null
    }
}

/**
 * Reads the native media feed from assets/native_media.json and maps a media type/id to a bundled
 * drawable resource id. Parsed once and cached. The JSON shape mirrors the future server feed:
 * `{ "media": [ { "id": "webp", "format": "webp", "surfaces": ["drawer"], "resource": "native_drawer_webp", "url": "" }, ... ] }`.
 *
 * `surfaces` is optional. An entry with `surfaces` applies only to those areas; an entry without it
 * is the default for every area. Lookup order for (surface, type): the exact surface
 * (e.g. "downloader_big"), then its base ("downloader"), then the default entry. A listed drawable
 * that is not in the app yet is skipped, so the next match in that order is used.
 */
object NativeMediaCatalog {

    private const val TAG = "NativeMediaCatalog"
    private const val ASSET = "native_media.json"
    private const val SERVER_FILE = "nativeAd_media.json"
    private const val TIMEOUT_MS = 8000

    // "surface|type" or plain "type" (lowercase) -> bundled drawable name
    @Volatile private var map: Map<String, String>? = null

    // Same keys -> absolute image URL, from the server feed. Empty until the feed has loaded.
    @Volatile private var serverMap: Map<String, String> = emptyMap()
    @Volatile private var serverBase: String? = null
    private val fetching = AtomicBoolean(false)

    /** Lookup keys for (surface, type), most specific first. */
    private fun candidates(type: String, surface: String): List<String> {
        val key = type.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        val s = surface.trim().lowercase()
        return buildList {
            if (s.isNotEmpty()) {
                add("$s|$key")
                if (s.startsWith("downloader_")) add("downloader|$key")
            }
            add(key)
        }
    }

    /** Server image URL for media [type] on [surface], or null when the feed isn't loaded / has none. */
    @JvmStatic
    fun serverUrlFor(type: String, surface: String): String? {
        if (serverMap.isEmpty()) refreshFromServer()   // lazy kick if startup fetch hasn't run yet
        val m = serverMap
        for (c in candidates(type, surface)) m[c]?.let { return it }
        return null
    }

    /**
     * Download {serverUrl}/{serverBaseFolderName}/nativeAd_media.json in the background and cache it.
     * Only when the override master switch is on (no network otherwise). Re-fetches when the base
     * folder changes; skips when it's already loaded for the current base. Failures keep the old map.
     */
    @JvmStatic
    fun refreshFromServer() {
        val cfg = app.messages.config.AdsConfigManager.config
        if (!cfg.nativeMediaOverrideOn) return
        val url = app.messages.config.AdsConfigManager.serverUrl()
        val folder = app.messages.config.AdsConfigManager.serverBaseFolder()
        if (url.isBlank() || folder.isBlank()) {
            Log.d(TAG, "no serverUrl/serverBaseFolderName → bundled media only")
            return
        }
        val base = "$url/$folder"
        if (base == serverBase && serverMap.isNotEmpty()) return
        if (!fetching.compareAndSet(false, true)) return
        Thread({
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$base/$SERVER_FILE").openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "fetch $SERVER_FILE -> HTTP ${conn.responseCode}")
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).optJSONArray("media") ?: JSONArray()
                val out = parseFeed(arr) { o ->
                    val v = o.optString("url").trim()
                    when {
                        v.isEmpty() -> null
                        v.startsWith("http://") || v.startsWith("https://") -> v
                        else -> "$base/${v.trimStart('/')}"
                    }
                }
                serverMap = out
                serverBase = base
                Log.d(TAG, "server feed loaded: ${out.size} keys from $base/$SERVER_FILE")
                // Feed is in: download the images now (background) so they're on disk before an ad shows.
                prefetchImages(out.values.toSet())
            } catch (t: Throwable) {
                Log.w(TAG, "server feed failed: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                conn?.disconnect()
                fetching.set(false)
            }
        }, "native-media-feed").start()
    }

    private val prefetched = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Warm everything the media override needs, entirely off the main thread: parses the bundled
     * catalog and kicks the server feed (whose images are then prefetched into Coil's disk cache).
     * Safe to call repeatedly (splash, Remote Config apply); no-op when the override is off.
     */
    @JvmStatic
    fun preloadAll(context: Context) {
        if (!app.messages.config.AdsConfigManager.config.nativeMediaOverrideOn) {
            Log.d(TAG, "preloadAll skipped: native_media_override_on=false (will retry when Remote Config applies)")
            return
        }
        val appCtx = context.applicationContext
        Log.d(TAG, "preloadAll START (bg thread) feedCached=${serverMap.size} keys")
        Thread({
            try {
                val bundled = catalog(appCtx)   // bundled fallback map, parsed once
                Log.d(TAG, "bundled catalog ready: ${bundled.size} keys")
                refreshFromServer()             // feed + images (prefetch runs after it loads)
                if (serverMap.isNotEmpty()) prefetchImages(serverMap.values.toSet())  // feed already cached: just images
            } catch (t: Throwable) {
                Log.w(TAG, "preloadAll failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }, "native-media-preload").start()
    }

    /** Enqueue Coil downloads (Coil runs them on its own background dispatcher) for URLs not yet requested. */
    private fun prefetchImages(urls: Set<String>) {
        val ctx = app.messages.Controller.getInstance()
        val loader = coil.Coil.imageLoader(ctx)
        for (u in urls) {
            if (!prefetched.add(u)) continue
            loader.enqueue(
                coil.request.ImageRequest.Builder(ctx)
                    .data(u)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)   // disk only: avoid holding big animated WebPs in RAM
                    .listener(onError = { _, r ->
                        prefetched.remove(u)
                        Log.w(TAG, "prefetch failed $u: ${r.throwable.message}")
                    }, onSuccess = { _, _ -> Log.d(TAG, "prefetched $u") })
                    .build()
            )
        }
    }

    /** Drawable resource id for media [type] ("image"/"webp"/an id) on [surface], or 0 if unresolved. */
    @JvmStatic
    @JvmOverloads
    fun drawableFor(context: Context, type: String, surface: String = ""): Int {
        val cat = catalog(context)
        for (c in candidates(type, surface)) {
            val name = cat[c] ?: continue
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) return id
            Log.w("NativeMediaCatalog", "drawable '$name' for '$c' not in app → trying next")
        }
        return 0
    }

    private fun catalog(context: Context): Map<String, String> {
        map?.let { return it }
        val parsed = synchronized(this) { map ?: parse(context).also { map = it } }
        return parsed
    }

    private fun parse(context: Context): Map<String, String> {
        return try {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val arr = JSONObject(json).optJSONArray("media") ?: return emptyMap()
            parseFeed(arr) { o -> o.optString("resource").trim().takeIf { it.isNotEmpty() } }
        } catch (t: Throwable) {
            Log.w(TAG, "parse failed: ${t.javaClass.simpleName}: ${t.message}")
            emptyMap()
        }
    }

    /**
     * Shared feed parser: each entry -> keys "surface|id" / "surface|format" (or plain "id"/"format"
     * when it has no `surfaces`) mapped to [valueOf] (a drawable name, or an image URL).
     */
    private fun parseFeed(arr: JSONArray, valueOf: (JSONObject) -> String?): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val resource = valueOf(o) ?: continue
                val surfaces = mutableListOf<String>()
                o.optJSONArray("surfaces")?.let { a ->
                    for (j in 0 until a.length()) a.optString(j).trim().lowercase().takeIf { it.isNotEmpty() }?.let(surfaces::add)
                }
                o.optString("surface").trim().lowercase().takeIf { it.isNotEmpty() }?.let(surfaces::add)
                val prefixes = if (surfaces.isEmpty()) listOf("") else surfaces.map { "$it|" }
                for (p in prefixes) {
                    o.optString("id").trim().lowercase().takeIf { it.isNotEmpty() }?.let { out[p + it] = resource }
                    o.optString("format").trim().lowercase().takeIf { it.isNotEmpty() }?.let { out.putIfAbsent(p + it, resource) }
                }
            }
        } catch (t: Throwable) {
            Log.w("NativeMediaCatalog", "parse failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        return out
    }
}
