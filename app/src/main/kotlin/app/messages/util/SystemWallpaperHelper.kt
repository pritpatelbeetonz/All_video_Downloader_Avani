package app.messages.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the device's current home wallpaper.
 *
 * Prefer showing wallpaper via the window (`windowShowWallpaper` + transparent backgrounds).
 * Use [applyTo] only when an ImageView copy is needed (e.g. drawer blur target).
 * Never falls back to a static app drawable — that would cover the real wallpaper.
 */
object SystemWallpaperHelper {

    suspend fun loadDrawable(context: Context): Drawable? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val wm = WallpaperManager.getInstance(appContext)

        // 1) Full drawable (user wallpaper when readable)
        runCatching { wm.drawable }.getOrNull()?.let { return@withContext it }

        // 2) Faster peek path
        runCatching { wm.fastDrawable }.getOrNull()?.let { return@withContext it }

        // 3) Decode system wallpaper file (works on more OEMs than getDrawable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                    val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    if (bitmap != null) BitmapDrawable(appContext.resources, bitmap) else null
                }
            }.getOrNull()?.let { return@withContext it }
        }

        null
    }

    /**
     * Sets a wallpaper copy on [imageView] when available; otherwise clears it so the
     * window-composited system wallpaper remains visible.
     */
    suspend fun applyTo(imageView: ImageView) {
        val drawable = loadDrawable(imageView.context)
        if (drawable != null) {
            imageView.setImageDrawable(drawable)
        } else {
            imageView.setImageDrawable(null)
        }
    }
}
