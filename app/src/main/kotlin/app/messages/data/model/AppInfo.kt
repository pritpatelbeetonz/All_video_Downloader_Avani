package app.messages.data.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val category: AppCategory = AppCategory.OTHERS,
    val isFavorite: Boolean = false,
    val canUninstall: Boolean = false
)

enum class AppCategory {
    ALL,
    FAVORITES,
    AUDIO,
    GAMES,
    PHOTOGRAPHY,
    PRODUCTIVITY,
    SOCIAL,
    VIDEO,
    OTHERS
}
