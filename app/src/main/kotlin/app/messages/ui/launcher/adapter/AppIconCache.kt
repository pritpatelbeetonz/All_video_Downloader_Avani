package app.messages.ui.launcher.adapter

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import app.messages.data.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared launcher icon cache.
 *
 * `PackageManager.getActivityIcon` is not cheap: it opens the target app's APK,
 * loads its resource table and decodes the icon. Without a cache every RecyclerView
 * bind paid that again, so returning to the launcher — which rebinds the whole
 * workspace, dock and drawer — re-decoded every visible icon from disk and left the
 * grid blank while it did.
 *
 * Icons are immutable for the lifetime of an installed version, so they are held in
 * an LRU keyed by component. A cached icon is applied synchronously during bind,
 * which is what removes the blank-then-pop-in flicker; only a miss goes to disk.
 */
object AppIconCache {

    /**
     * Sized off the heap rather than a fixed entry count: an adaptive icon can hold
     * two full-resolution layers, so a count-based cap would swing between trivial
     * and tens of megabytes across devices. The launcher wants to stay small — a
     * fat home process is the first thing the OS kills.
     */
    private val cache: LruCache<String, Drawable> = object : LruCache<String, Drawable>(
        (Runtime.getRuntime().maxMemory() / 16)
            .coerceIn(4L * 1024 * 1024, 24L * 1024 * 1024)
            .toInt()
    ) {
        override fun sizeOf(key: String, value: Drawable): Int = estimateBytes(value)
    }

    /**
     * Icon decoding is disk-bound, and the old code fanned every bind out onto the
     * shared IO pool — a fast drawer fling could put 40 decodes on 64 threads at once
     * and they simply contended. Four at a time keeps the first screenful arriving
     * sooner.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(4)
    )

    private fun keyOf(app: AppInfo): String = app.packageName + "/" + app.activityName

    /** Cached icon for [app], or null when it has not been loaded yet. */
    fun peek(app: AppInfo): Drawable? = cache.get(keyOf(app))

    /**
     * Shows [app]'s icon in [target].
     *
     * On a cache hit the icon is set immediately and null is returned — no job, no
     * flicker. On a miss the previous image is left in place only when the view is
     * not already showing a different app; the returned [Job] should be cancelled by
     * the ViewHolder on its next bind.
     */
    fun bind(target: ImageView, app: AppInfo): Job? {
        val key = keyOf(app)
        val cached = cache.get(key)
        if (cached != null) {
            target.tag = key
            target.setImageDrawable(cached)
            return null
        }
        // Only blank when the view is recycled from another app, so a rebind of the
        // same icon (onResume, a settings change) never flashes empty.
        if (target.tag != key) {
            target.tag = key
            target.setImageDrawable(null)
        }
        val context = target.context.applicationContext
        return scope.launch {
            val drawable = load(context, app) ?: return@launch
            withContext(Dispatchers.Main) {
                // The holder may have been recycled onto another app mid-decode.
                if (target.tag == key) target.setImageDrawable(drawable)
            }
        }
    }

    /** Drops [packageName]'s icons so a reinstall or update re-reads them. */
    fun invalidate(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys
            .filter { it.startsWith(prefix) }
            .forEach { cache.remove(it) }
    }

    private fun load(context: Context, app: AppInfo): Drawable? {
        val key = keyOf(app)
        cache.get(key)?.let { return it }
        val pm = context.packageManager
        val drawable = try {
            pm.getActivityIcon(ComponentName(app.packageName, app.activityName))
        } catch (_: Exception) {
            try {
                pm.getApplicationIcon(app.packageName)
            } catch (_: Exception) {
                null
            }
        } ?: return null
        cache.put(key, drawable)
        return drawable
    }

    private fun estimateBytes(drawable: Drawable): Int {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it.byteCount.coerceAtLeast(1) }
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_EDGE_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_EDGE_PX
        val layers = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable
        ) 2 else 1
        return width * height * 4 * layers
    }

    private const val FALLBACK_EDGE_PX = 96
}
