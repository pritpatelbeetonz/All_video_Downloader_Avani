package app.messages.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Fixed PIN length for app lock (§F16). 4 digits, auto-submits on the last key. */
const val PIN_LENGTH = 4

/** Row of dots showing how many PIN digits have been entered. */
@Composable
fun PinDots(filled: Int, total: Int = PIN_LENGTH, error: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val on = if (error) scheme.error else scheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) on else scheme.onSurface.copy(alpha = 0.15f)),
            )
        }
    }
}

/**
 * 3×4 numeric keypad. The bottom-left slot holds an optional [trailing] action
 * (e.g. a biometric button); the bottom-right is backspace.
 */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (rowIndex in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (col in 1..3) {
                    val n = rowIndex * 3 + col
                    KeypadKey(label = n.toString()) { onDigit(n) }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                trailing?.invoke()
            }
            KeypadKey(label = "0") { onDigit(0) }
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
    }
}
