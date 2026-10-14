package app.messages.ui.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.contacts.ContactsResolver
import app.messages.data.scheduling.ScheduledMessage
import app.messages.data.scheduling.ScheduledMessageRepository
import app.messages.monetization.iap.EntitlementController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Scheduled queue screen (§F13). */
@HiltViewModel
class ScheduledViewModel @Inject constructor(
    private val repository: ScheduledMessageRepository,
    entitlement: EntitlementController,
    private val contacts: ContactsResolver,
) : ViewModel() {

    val pending: StateFlow<List<ScheduledMessage>> =
        repository.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPremium: StateFlow<Boolean> = entitlement.isPremium

    /** Cached contact lookup for the recipient label. */
    fun displayName(address: String): String =
        contacts.resolve(address).displayName ?: address

    fun cancel(id: Long) {
        viewModelScope.launch { repository.cancel(id) }
    }

    fun sendNow(id: Long) {
        viewModelScope.launch { repository.sendNow(id) }
    }

    fun reschedule(id: Long, atMs: Long) {
        viewModelScope.launch { repository.reschedule(id, atMs) }
    }
}
