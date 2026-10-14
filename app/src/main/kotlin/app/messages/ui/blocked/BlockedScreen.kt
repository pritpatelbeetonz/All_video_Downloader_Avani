package app.messages.ui.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.blocking.BlockingRepository
import app.messages.data.contacts.ContactsResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.ui.components.EmptyState
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing

data class BlockedRow(val number: String, val displayName: String)

@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val blockingRepository: BlockingRepository,
    private val contacts: ContactsResolver,
) : ViewModel() {
    val blocked: StateFlow<List<BlockedRow>> = blockingRepository.blocked
        .map { list -> list.map { BlockedRow(it, contacts.resolve(it).displayName ?: it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unblock(number: String) {
        viewModelScope.launch { blockingRepository.unblock(number) }
    }
}

/** Blocked & spam management (§F9). */
@Composable
fun BlockedScreen(
    onBack: () -> Unit,
    vm: BlockedViewModel = hiltViewModel(),
) {
    val items by vm.blocked.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Blocked & spam", onBack = onBack) {
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Block,
                text = "No blocked numbers",
                hint = "Numbers you block can't text or call you",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.number }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ContactAvatar(row.number, row.displayName, null, isGroup = false, size = 40.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                                if (row.displayName != row.number) {
                                    Text(
                                        row.number,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { vm.unblock(row.number) }) { Text("Unblock") }
                    }
                }
            }
        }
    }
}
