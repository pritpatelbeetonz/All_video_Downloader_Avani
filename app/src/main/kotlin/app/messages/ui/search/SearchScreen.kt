package app.messages.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.core.time.formatRelativeTimestamp
import app.messages.data.contacts.ContactSuggestion
import app.messages.data.telephony.MessageSearchResult
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing
import app.messages.ui.theme.appCardBg
import app.messages.ui.theme.appScreenBg
import java.util.Date

/**
 * Full-text search across messages + contacts — build doc §F8. Debounced;
 * provider-backed for M2. Aligned to the app surface: [appScreenBg] + a custom
 * header with a rounded search field (matching Home) instead of a Material bar.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appScreenBg())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(appCardBg())
                    .border(1.dp, scheme.outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search messages", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, "Clear", tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).clickable { vm.onQueryChange("") },
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                query.length < 2 -> Hint("Search across your messages and contacts")
                results.isEmpty -> Hint("No matches for “$query”")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (results.contacts.isNotEmpty()) {
                        item { SectionHeader("Contacts") }
                        items(results.contacts, key = { "c_${it.number}" }) { c ->
                            ContactResultRow(c) {
                                vm.openContact(c.number) { threadId -> onOpenThread(threadId) }
                            }
                        }
                    }
                    if (results.messages.isNotEmpty()) {
                        item { SectionHeader("Messages") }
                        items(results.messages, key = { "m_${it.messageId}" }) { m ->
                            MessageResultRow(m, query) { onOpenThread(m.threadId) }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ContactResultRow(contact: ContactSuggestion, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        ContactAvatar(contact.number, contact.name, contact.photoUri, isGroup = false, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(contact.name, style = MaterialTheme.typography.titleMedium)
            Text(
                contact.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun MessageResultRow(result: MessageSearchResult, query: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        ContactAvatar(
            result.address,
            result.contactName,
            null,
            isGroup = false,
            size = 40.dp,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                result.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            formatRelativeTimestamp(Date(result.dateEpochMs)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.W700,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.screenEdge, end = Spacing.screenEdge,
            top = Spacing.lg, bottom = Spacing.sm,
        ),
    )
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
