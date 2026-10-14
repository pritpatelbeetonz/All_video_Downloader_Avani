package app.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.messages.ui.theme.Spacing

/**
 * Shared empty state per build doc §5.3: a soft tonal-circle illustration, a
 * single line of copy, and an optional supporting hint. One accent color (§5.1),
 * works in light + dark. The only allowed emoji lives in the [text] callers pass.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = scheme.primary.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.size(Spacing.lg))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
