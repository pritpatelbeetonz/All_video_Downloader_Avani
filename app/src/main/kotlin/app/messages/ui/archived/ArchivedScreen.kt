package app.messages.ui.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.telephony.ConversationRepository
import app.messages.data.telephony.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.ui.components.EmptyState
import app.messages.ui.components.ScreenScaffold
import app.messages.ui.conversations.ConversationTile

@HiltViewModel
class ArchivedViewModel @Inject constructor(
    private val repository: ConversationRepository,
) : ViewModel() {
    val archived: StateFlow<List<Conversation>> = repository.watchConversations()
        .map { all -> all.filter { it.isArchived }.sortedByDescending { it.lastActivity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unarchive(threadId: String) {
        viewModelScope.launch { repository.setArchived(threadId, false) }
    }
}

/** Archived conversations (§F10). Tap opens the thread; the action unarchives. */
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    vm: ArchivedViewModel = hiltViewModel(),
) {
    val items by vm.archived.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Archived", onBack = onBack) {
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Inbox,
                text = "No archived conversations",
                hint = "Swipe a chat to archive it",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.threadId }) { c ->
                    ConversationTile(
                        conversation = c,
                        onTap = { onOpenThread(c.threadId) },
                        onLongPress = { vm.unarchive(c.threadId) },
                    )
                }
            }
        }
    }
}
