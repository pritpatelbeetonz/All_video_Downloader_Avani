package app.messages.ads.ng

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import org.json.JSONObject

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
 * The asset for a type is resolved through [NativeMediaCatalog], which reads the media feed from
 * assets/native_media.json (id -> bundled drawable). That file is the local stand-in for a future
 * server feed of the same shape — swapping the source later needs no change here.
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
            "downloader" -> c.nativeMediaDownloader
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
        val resId = NativeMediaCatalog.drawableFor(adView.context, type)
        if (resId == 0) {
            Log.w("NativeMediaOverride", "no asset for type='$type' on surface='$surface' → keep real media")
            return mediaView
        }

        val parent = mediaView.parent as? ViewGroup ?: return mediaView
        val index = parent.indexOfChild(mediaView)
        val params = mediaView.layoutParams
        val image = ImageView(adView.context).apply {
            layoutParams = params
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(resId)
        }
        parent.removeView(mediaView)
        parent.addView(image, index)
        return null
    }
}

/**
 * Reads the native media feed from assets/native_media.json and maps a media type/id to a bundled
 * drawable resource id. Parsed once and cached. The JSON shape mirrors the future server feed:
 * `{ "media": [ { "id": "image", "format": "png", "resource": "demo", "url": "" }, ... ] }`.
 */
object NativeMediaCatalog {

    private const val ASSET = "native_media.json"

    // type/id/format (lowercase) -> bundled drawable name
    @Volatile private var map: Map<String, String>? = null

    /** Drawable resource id for the given media [type] ("image"/"webp"/an id), or 0 if unresolved. */
    @JvmStatic
    fun drawableFor(context: Context, type: String): Int {
        val key = type.trim().lowercase()
        if (key.isEmpty()) return 0
        val name = catalog(context)[key] ?: return 0
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private fun catalog(context: Context): Map<String, String> {
        map?.let { return it }
        val parsed = synchronized(this) { map ?: parse(context).also { map = it } }
        return parsed
    }

    private fun parse(context: Context): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val arr = JSONObject(json).optJSONArray("media") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val resource = o.optString("resource").trim()
                if (resource.isEmpty()) continue
                o.optString("id").trim().lowercase().takeIf { it.isNotEmpty() }?.let { out[it] = resource }
                o.optString("format").trim().lowercase().takeIf { it.isNotEmpty() }?.let { out.putIfAbsent(it, resource) }
            }
        } catch (t: Throwable) {
            Log.w("NativeMediaCatalog", "parse failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        return out
    }
}
