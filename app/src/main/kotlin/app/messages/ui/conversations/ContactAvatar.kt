package app.messages.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.messages.ui.theme.Spacing
import kotlin.math.absoluteValue

/**
 * 40dp circle avatar — contact photo when present, otherwise an initial on
 * a tonal background derived from a stable hash of the address. Per build
 * doc §5.3. Photo loading lands with Coil integration in M1.5.
 */
@Composable
fun ContactAvatar(
    address: String,
    name: String?,
    photoUri: String?,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.avatarSize,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val anchorHue = remember(tertiary) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(tertiary.toArgb(), hsv)
        hsv[0]
    }
    val (bg, fg) = remember(address, anchorHue) { tintFor(address, anchorHue) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
    ) {
        when {
            // Photo loading via Coil ships in M1.5 — for now we render the
            // initial fallback even when photoUri is set.
            isGroup -> Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = fg,
            )
            else -> Text(
                text = initialFor(name, address),
                color = fg,
                fontWeight = FontWeight.W600,
                fontSize = (size.value * 0.42f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun initialFor(name: String?, address: String): String {
    val source = if (!name.isNullOrBlank()) name else address
    for (ch in source) {
        if (ch.isLetter()) return ch.uppercaseChar().toString()
    }
    for (ch in source) {
        if (ch.isDigit()) return ch.toString()
    }
    return "#"
}

private fun tintFor(address: String, anchorHue: Float): Pair<Color, Color> {
    val slot = (stableHash(address) % 10).absoluteValue
    val hue = ((anchorHue + slot * 36f) % 360f + 360f) % 360f
    val bg = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.38f, 0.78f)))
    val fg = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.22f)))
    return bg to fg
}

private fun stableHash(s: String): Int {
    var h = 0
    for (ch in s) h = (h * 31 + ch.code) and 0x7fffffff
    return h
}
