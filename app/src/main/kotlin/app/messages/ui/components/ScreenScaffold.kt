package app.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.ui.theme.appScreenBg

/**
 * Shared screen scaffold matching the app's redesigned surfaces (Settings / Home /
 * Notifications, Figma 43:1213): the [appScreenBg] background + a lightweight
 * custom header (back chevron + Inter title) instead of a Material `TopAppBar`.
 *
 * Brings the secondary screens (Archived/Blocked/Starred/Scheduled/Backup/App-lock)
 * into the same visual language so the whole app reads as one design. Theme-aware
 * via [appScreenBg] + `colorScheme`. [content] runs in a `ColumnScope` below the
 * header, so callers can `weight(1f)` a list or center an empty state.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val font = MaterialTheme.typography.titleMedium.fontFamily
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appScreenBg())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "Back",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                fontFamily = font,
                fontWeight = FontWeight.W700,
                fontSize = 22.sp,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        // Default content colour for the body, so text/icons without an explicit
        // colour follow the theme (otherwise they fall back to black — invisible
        // in dark mode, e.g. the App-lock "Create a PIN" title + backspace icon).
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}
