package app.messages.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.messages.monetization.ads.NativeAdContent
import app.messages.ui.theme.Spacing

/**
 * Native ad rendered as a *clearly-labeled* conversation tile (build doc §6.2
 * A1). It deliberately mirrors the [ConversationTile] layout — 72dp, avatar,
 * title, snippet — but carries a "Sponsored" chip and a distinct surface tint
 * so it never deceives the user into thinking it's a real conversation. That
 * honesty is what protects the rating.
 */
@Composable
fun SponsoredTile(
    ad: NativeAdContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.tileHeight)
            // Distinct tint marks it as not-a-conversation.
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.avatarSize)
                .clip(CircleShape)
                .background(scheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Campaign,
                contentDescription = null,
                tint = scheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ad.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.sm))
                SponsoredChip()
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = ad.body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Spacing.pillRadius))
                .background(scheme.primary)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = 6.dp),
        ) {
            Text(
                text = ad.callToAction,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                color = scheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SponsoredChip() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.tertiary.copy(alpha = 0.18f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "Sponsored",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = scheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
