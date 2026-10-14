package app.messages.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.contacts.ContactSuggestion
import app.messages.data.contacts.ContactsResolver
import app.messages.data.telephony.MessageRepository
import app.messages.data.telephony.SimInfo
import app.messages.data.telephony.SimRepository
import app.messages.data.telephony.SmsSegments
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A chosen recipient — name when known, otherwise just the number. */
data class Recipient(val name: String?, val address: String) {
    val label: String get() = name ?: address
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val contacts: ContactsResolver,
    private val simRepository: SimRepository,
    private val segments: SmsSegments,
) : ViewModel() {

    private val _recipients = MutableStateFlow<List<Recipient>>(emptyList())
    val recipients: StateFlow<List<Recipient>> = _recipients

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val suggestions: StateFlow<List<ContactSuggestion>> = _query
        .debounce(180)
        .flatMapLatest { q ->
            flow {
                if (q.isBlank()) emit(emptyList())
                else emit(withContext(Dispatchers.IO) { contacts.search(q, limit = 8) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    val segmentInfo = _draft
        .map { segments.compute(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            segments.compute(""),
        )

    val sims: List<SimInfo> = simRepository.activeSims()
    private val _selectedSim = MutableStateFlow(sims.firstOrNull())
    val selectedSim: StateFlow<SimInfo?> = _selectedSim

    val canSend: StateFlow<Boolean> = combine(_recipients, _draft) { r, d ->
        r.isNotEmpty() && d.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun cycleSim() {
        if (sims.size < 2) return
        val current = _selectedSim.value ?: sims.first()
        _selectedSim.value = sims[(sims.indexOf(current) + 1) % sims.size]
    }

    fun addRecipient(suggestion: ContactSuggestion) {
        addRecipient(Recipient(suggestion.name, suggestion.number))
        _query.value = ""
    }

    /** Commit whatever raw text is in the field as a number recipient. */
    fun commitTypedRecipient() {
        val raw = _query.value.trim()
        if (raw.isBlank()) return
        addRecipient(Recipient(name = null, address = raw))
        _query.value = ""
    }

    private fun addRecipient(recipient: Recipient) {
        if (_recipients.value.any { it.address == recipient.address }) return
        _recipients.value = _recipients.value + recipient
    }

    fun removeRecipient(recipient: Recipient) {
        _recipients.value = _recipients.value - recipient
    }

    /**
     * Sends the draft to every recipient. Returns the thread id to navigate to
     * for a single recipient, or null for multi-recipient (pop back to list).
     */
    fun send(onResult: (threadId: String?) -> Unit) {
        val body = _draft.value.trim()
        val targets = _recipients.value
        if (body.isEmpty() || targets.isEmpty()) {
            onResult(null)
            return
        }
        val subId = _selectedSim.value?.subscriptionId
        viewModelScope.launch {
            val threadId = withContext(Dispatchers.IO) {
                targets.forEach { repository.send(it.address, body, subId) }
                if (targets.size == 1) repository.resolveThreadId(targets.first().address)
                else null
            }
            _draft.value = ""
            onResult(threadId?.takeIf { it.isNotBlank() })
        }
    }
}
