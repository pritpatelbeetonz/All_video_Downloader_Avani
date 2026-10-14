package app.messages.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.scheduling.ScheduleRules
import app.messages.data.scheduling.ScheduledMessageRepository
import app.messages.data.star.StarRepository
import app.messages.data.telephony.MessageRepository
import app.messages.data.telephony.SimInfo
import app.messages.data.telephony.SimRepository
import app.messages.data.telephony.ThreadHeader
import app.messages.data.telephony.model.SmsMessage
import app.messages.ui.nav.Routes
import app.messages.ui.scheduling.formatScheduledAt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ThreadUiState(
    val header: ThreadHeader? = null,
    val items: List<ThreadItem> = emptyList(),
    val isLoading: Boolean = true,
)

private const val PAGE_SIZE = 50

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val simRepository: SimRepository,
    private val starRepository: StarRepository,
    private val scheduled: ScheduledMessageRepository,
) : ViewModel() {

    private val threadId: String = savedStateHandle[Routes.ThreadArg] ?: ""

    private val limit = MutableStateFlow(PAGE_SIZE)

    private val _header = MutableStateFlow<ThreadHeader?>(null)

    /** True once the provider has returned fewer rows than we asked for. */
    private val endReached = MutableStateFlow(false)

    private val messages: StateFlow<List<SmsMessage>> = limit
        .flatMapLatest { requested ->
            repository.watchMessages(threadId, requested)
                .onEach { rows -> endReached.value = rows.size < requested }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val starredIds: StateFlow<Set<String>> =
        starRepository.observeStarredIdsForThread(threadId)
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<ThreadUiState> =
        combine(_header, messages, starredIds) { header, msgs, starred ->
            // Copying every message just to stamp isStarred=false gave each bubble a
            // new identity on every emission, so all of them recomposed. Nothing is
            // starred in the common case — leave the list alone then.
            val withStars =
                if (starred.isEmpty()) msgs
                else msgs.map { it.copy(isStarred = it.id in starred) }
            ThreadUiState(
                header = header,
                items = buildThreadItems(withStars),
                isLoading = false,
            )
        }
            // buildThreadItems walks every loaded message doing date math and
            // allocating a ThreadItem each. viewModelScope is Main, so on a paged-up
            // thread that whole pass was running on the UI thread every time the
            // provider so much as twitched.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState())

    // ── Composer state ─────────────────────────────────────────────────────────
    val sims: List<SimInfo> = simRepository.activeSims()
    private val _selectedSim = MutableStateFlow(sims.firstOrNull())
    val selectedSim: StateFlow<SimInfo?> = _selectedSim

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment

    /** One-shot user-facing messages for the schedule flow (shown as snackbars). */
    private val _scheduleEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scheduleEvents: SharedFlow<String> = _scheduleEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            _header.value = repository.getHeader(threadId)
        }
        viewModelScope.launch { repository.markThreadRead(threadId) }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun appendToDraft(value: String) {
        val current = _draft.value
        _draft.value = if (current.isEmpty()) value else "$current\n$value"
    }

    fun cycleSim() {
        if (sims.size < 2) return
        val current = _selectedSim.value ?: sims.first()
        val idx = sims.indexOf(current)
        _selectedSim.value = sims[(idx + 1) % sims.size]
    }

    fun setAttachment(attachment: PendingAttachment) {
        _pendingAttachment.value = attachment
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun send() {
        val address = _header.value?.primaryAddress
        if (address.isNullOrBlank()) return
        val body = _draft.value.trim()
        val attachment = _pendingAttachment.value
        when {
            attachment != null -> {
                _draft.value = ""
                _pendingAttachment.value = null
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.sendMms(address, body, attachment.uri.toString(), attachment.mimeType)
                    }
                }
            }
            body.isNotEmpty() -> {
                _draft.value = ""
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.send(address, body, _selectedSim.value?.subscriptionId)
                    }
                }
            }
        }
    }

    /** Queues the current draft to send at [atMs] (§F13). Validates via [ScheduleRules]. */
    fun scheduleDraft(atMs: Long) {
        val address = _header.value?.primaryAddress
        val body = _draft.value.trim()
        if (address.isNullOrBlank()) return
        if (body.isEmpty()) {
            _scheduleEvents.tryEmit("Type a message to schedule")
            return
        }
        viewModelScope.launch {
            val check = scheduled.schedule(
                threadId = threadId,
                address = address,
                body = body,
                simSlot = _selectedSim.value?.subscriptionId,
                atMs = atMs,
            )
            val message = when (check) {
                ScheduleRules.Check.Ok -> {
                    _draft.value = ""
                    "Scheduled for ${formatScheduledAt(atMs)}"
                }
                ScheduleRules.Check.AtCap ->
                    "Free limit is ${ScheduleRules.FREE_MAX_ACTIVE} scheduled messages — go Premium for unlimited"
                ScheduleRules.Check.InPast -> "Pick a time in the future"
                ScheduleRules.Check.TooFar -> "That's too far in the future"
            }
            _scheduleEvents.tryEmit(message)
        }
    }

    fun retry(message: SmsMessage) {
        // Re-send the failed message body to the same address, then drop the
        // failed row so we don't leave a duplicate. Full retry-in-place is M3.
        val address = _header.value?.primaryAddress ?: message.address
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.send(address, message.body, message.simSlot)
                repository.deleteMessage(message.id)
            }
        }
    }

    fun deleteMessage(message: SmsMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteMessage(message.id) }
        }
    }

    fun toggleStar(message: SmsMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                starRepository.setStarred(message, !message.isStarred)
            }
        }
    }

    /**
     * Grows the page window. Without the guards, sitting at the top of a thread
     * kept bumping the limit forever — each bump re-ran the provider query and
     * rebuilt every item, which is exactly when the list needs to be cheapest.
     */
    fun loadMore() {
        if (endReached.value) return
        if (messages.value.isEmpty()) return // nothing has loaded yet
        limit.value += PAGE_SIZE
    }

    val displayName: StateFlow<String> = _header
        .map { it?.displayName ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
