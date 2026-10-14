package app.messages.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.ui.theme.appCardBg

/**
 * Shared Settings design-system rows (Figma 43:1213): a labelled section header,
 * a rounded card group, and a row that renders as a value+chevron, a toggle, or a
 * plain navigation row. Used by [SettingsScreen] and [NotificationSettingsScreen]
 * so both stay visually identical and theme-aware.
 */
@Composable
fun SectionLabel(text: String, font: FontFamily?) {
    Text(
        text,
        fontFamily = font, fontWeight = FontWeight.W600, fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appCardBg()),
    ) { content() }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    font: FontFamily?,
    subtitle: String? = null,
    value: String? = null,
    toggle: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) scheme.error else scheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = font, fontWeight = FontWeight.W600, fontSize = 16.sp, color = if (destructive) scheme.error else scheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontFamily = font, fontWeight = FontWeight.W400, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, lineHeight = 16.sp)
            }
        }
        when {
            toggle != null && onToggle != null -> Switch(
                checked = toggle, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary, checkedThumbColor = scheme.onPrimary),
            )
            value != null -> {
                Text(value, fontFamily = font, fontWeight = FontWeight.W500, fontSize = 15.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            onClick != null -> Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (divider) {
        Box(modifier = Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(scheme.onSurface.copy(alpha = 0.07f)))
    }
}
