package app.messages.ui.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.contacts.ContactsResolver
import app.messages.data.db.entity.StarredMessageEntity
import app.messages.data.star.StarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.core.time.formatRelativeTimestamp
import app.messages.ui.components.EmptyState
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing
import java.util.Date

data class StarredRow(
    val entity: StarredMessageEntity,
    val displayName: String,
)

@HiltViewModel
class StarredViewModel @Inject constructor(
    private val starRepository: StarRepository,
    private val contacts: ContactsResolver,
) : ViewModel() {
    val starred: StateFlow<List<StarredRow>> = starRepository.observeAll()
        .map { list ->
            list.map { e ->
                StarredRow(e, contacts.resolve(e.address).displayName ?: e.address)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unstar(messageId: String) {
        viewModelScope.launch { starRepository.unstar(messageId) }
    }
}

/** Starred messages (§F20). Tap opens the thread; the action unstars. */
@Composable
fun StarredScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    vm: StarredViewModel = hiltViewModel(),
) {
    val items by vm.starred.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Starred", onBack = onBack) {
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.StarBorder,
                text = "No starred messages yet",
                hint = "Long-press a message, then Star",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.entity.messageId }) { row ->
                    StarredItemRow(
                        row = row,
                        onOpen = { onOpenThread(row.entity.threadId) },
                        onUnstar = { vm.unstar(row.entity.messageId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredItemRow(row: StarredRow, onOpen: () -> Unit, onUnstar: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        ContactAvatar(row.entity.address, row.displayName, null, isGroup = false, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    formatRelativeTimestamp(Date(row.entity.dateEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                row.entity.body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        IconButton(onClick = onUnstar) {
            Icon(Icons.Outlined.Star, contentDescription = "Unstar", tint = scheme.primary)
        }
    }
}
