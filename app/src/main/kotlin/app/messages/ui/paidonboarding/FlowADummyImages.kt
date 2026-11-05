package app.messages.ui.paidonboarding

import android.util.Log
import app.messages.config.AdsConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Holds the Flow-A DUMMY ad images, fetched from the server configured in Remote Config
 * (serverUrl + serverBaseFolderName). Two JSON files — native.json and banner.json — each a tagged
 * list `image1`..`imageN` mapping 1:1 to the Flow-A pages (1 = first page). A value may be a full
 * http(s) URL or a bare filename (resolved against the base folder). Missing/blank => no dummy on
 * that page.
 *
 * Fetch once per Flow-A entry; the maps are cached in memory and Glide caches the images themselves.
 * All failures are swallowed (no dummy shown) — the funnel must never break because the CDN is down.
 *
 * Logcat: tag:FlowAAds
 */
object FlowADummyImages {

    private const val TAG = "FlowAAds"
    private const val PAGE_COUNT = 11
    private const val TIMEOUT_MS = 8000

    @Volatile private var nativeImages: Map<Int, String> = emptyMap()
    @Volatile private var bannerImages: Map<Int, String> = emptyMap()
    @Volatile private var fetched = false

    fun nativeImage(pageNumber: Int): String? = nativeImages[pageNumber]
    fun bannerImage(pageNumber: Int): String? = bannerImages[pageNumber]

    /** Base = {serverUrl}/{serverBaseFolderName}, or null when either is not configured. */
    private fun base(): String? {
        val url = AdsConfigManager.serverUrl()
        val folder = AdsConfigManager.serverBaseFolder()
        if (url.isBlank() || folder.isBlank()) return null
        return "$url/$folder"
    }

    /** Resolve a JSON value: a full http(s) URL stays as-is; a bare filename is joined to [base]. */
    private fun resolve(base: String, value: String): String? {
        val v = value.trim()
        if (v.isBlank()) return null
        return if (v.startsWith("http://") || v.startsWith("https://")) v else "$base/${v.trimStart('/')}"
    }

    /** GET a JSON file and parse image1..imageN into a page->url map. Returns empty on any failure. */
    private fun fetchJson(base: String, file: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$base/$file").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "fetch $file -> HTTP ${conn.responseCode}")
                return emptyMap()
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            for (i in 1..PAGE_COUNT) {
                val raw = json.optString("image$i", "")
                resolve(base, raw)?.let { out[i] = it }
            }
            Log.d(TAG, "fetched $file: ${out.size} images -> $out")
        } catch (t: Throwable) {
            Log.w(TAG, "fetch $file failed: ${t.javaClass.simpleName}: ${t.message}")
            return emptyMap()
        } finally {
            conn?.disconnect()
        }
        return out
    }

    /** Fetch both JSONs on IO. Safe to call repeatedly; re-fetches each Flow-A entry. */
    suspend fun fetch() {
        val base = base() ?: run {
            Log.d(TAG, "no serverUrl/serverBaseFolderName configured — no dummy images")
            nativeImages = emptyMap(); bannerImages = emptyMap(); fetched = true
            return
        }
        withContext(Dispatchers.IO) {
            nativeImages = fetchJson(base, "native.json")
            bannerImages = fetchJson(base, "banner.json")
            fetched = true
        }
    }

    fun isFetched(): Boolean = fetched
}
