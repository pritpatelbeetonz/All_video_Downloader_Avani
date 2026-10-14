package app.messages.ui.conversations

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.core.prefs.AppPrefs
import app.messages.core.prefs.SwipeAction
import app.messages.data.blocking.BlockingRepository
import app.messages.data.role.RoleStateRepository
import app.messages.data.role.SmsRoleStatus
import app.messages.data.telephony.ConversationRepository
import app.messages.data.telephony.model.Conversation
import app.messages.monetization.ads.AdsController
import app.messages.monetization.ads.NativeAdManager
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MessageCategory {
    All, Personal, OTP, Transactions, Unread, Archived
}

/** UI state for [ConversationListScreen]. */
data class ConversationListUiState(
    val pinned: List<Conversation> = emptyList(),
    val others: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val selectedCategory: MessageCategory = MessageCategory.All,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val blockingRepository: BlockingRepository,
    private val adsController: AdsController,
    private val nativeAdManager: NativeAdManager,
    prefs: AppPrefs,
    private val roleStateRepository: RoleStateRepository,
) : ViewModel() {

    val swipeAction: StateFlow<SwipeAction> =
        prefs.swipeAction.stateIn(viewModelScope, SharingStarted.Eagerly, SwipeAction.Archive)

    /** Whether to interleave Sponsored tiles (gated; OFF by default). */
    val nativeListAdsEnabled: StateFlow<Boolean> = adsController.nativeListAdsEnabled

    /** Content indices after which a Sponsored tile is inserted. */
    fun adSlots(contentCount: Int): Set<Int> =
        adsController.nativeAdSlots(contentCount).toSet()

    /** Dynamic map of slot ordinals to distinct AdMob native ads. */
    val assignedAds: StateFlow<Map<Int, NativeAd>> = nativeAdManager.activeSlotMap

    private val _selectedCategory = MutableStateFlow(MessageCategory.All)

    /**
     * True between the moment the default-SMS role flips and the first inbox
     * snapshot that flip produces. Granting the role is what makes the provider
     * readable at all, so the list would otherwise sit on its stale empty state
     * for the length of the re-query; this shows the loader instead.
     */
    private val _reloadingAfterRoleChange = MutableStateFlow(false)

    private val _isScreenVisible = MutableStateFlow(false)

    val uiState: StateFlow<ConversationListUiState> =
        combine(
            // Every snapshot that lands clears the post-role-change loader — the
            // role flip itself is one of the repository's re-query triggers, so the
            // next emission here is that reload's result.
            repository.watchConversations()
                .onEach { _reloadingAfterRoleChange.value = false },
            blockingRepository.blocked,
            _selectedCategory,
            _reloadingAfterRoleChange,
        ) { all, blocked, category, reloading ->
            val blockedSet = blocked.toSet()
            val visible = all.filter { c ->
                c.primaryAddress !in blockedSet && c.matchesCategory(category)
            }
            val sorted = visible.sortedWith(
                compareByDescending<Conversation> { it.isPinned }
                    .thenByDescending { it.lastActivity },
            )
            ConversationListUiState(
                pinned = sorted.filter { it.isPinned },
                others = sorted.filterNot { it.isPinned },
                isLoading = reloading,
                selectedCategory = category,
            )
        }
            // viewModelScope is Dispatchers.Main.immediate, so without this the
            // block above — a category filter that lowercases and substring-scans
            // every snippet, plus a full sort and two more passes — ran on the main
            // thread on every provider notification. On a large mailbox that is a
            // dropped frame each time, landing right in the middle of a scroll.
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ConversationListUiState(),
            )

    init {
        // Dynamically request ad for first slot ONLY when the screen is visible, ads are eligible, and slots exist
        viewModelScope.launch {
            combine(_isScreenVisible, nativeListAdsEnabled, uiState) { visible, adsEnabled, state ->
                val slots = adSlots(state.others.size)
                android.util.Log.d("ChatAds", "🎯 [ChatAds] STATUS CHECK ──> visible=$visible, adsEnabled=$adsEnabled, totalConvos=${state.others.size}, slotCount=${slots.size}")
                visible && adsEnabled && slots.isNotEmpty()
            }.collect { shouldLoad ->
                if (shouldLoad) {
                    android.util.Log.d("ChatAds", "🚀 [ChatAds] TRIGGER: Screen Visible & Eligible with Slots ──> Settling initial ad slot (0)")
                    nativeAdManager.onSlotSettled(0)
                }
            }
        }

        viewModelScope.launch {
            // drop(1): the value replayed on subscribe is the state we already
            // rendered, not a change the user just made.
            roleStateRepository.state.drop(1).collect {
                _reloadingAfterRoleChange.value = true
            }
        }
    }

    private var lastHiddenTimestampMs: Long = 0L

    fun onScreenVisible() {
        android.util.Log.d("ChatAds", "📱 [ChatAds] 👁️ SCREEN BECAME VISIBLE (Chats Tab Active / Resumed)")
        _isScreenVisible.value = true

        if (nativeListAdsEnabled.value && adSlots(uiState.value.others.size).isNotEmpty()) {
            if (lastHiddenTimestampMs > 0L) {
                android.util.Log.d("ChatAds", "🔄 [ChatAds] User returned to Chats tab ──> Triggering onScreenReturned(0)")
                nativeAdManager.onScreenReturned(0)
            } else {
                nativeAdManager.onSlotSettled(0)
            }
        }
    }

    fun onScreenHidden() {
        android.util.Log.d("ChatAds", "🙈 [ChatAds] SCREEN HIDDEN (User left Chats tab)")
        lastHiddenTimestampMs = System.currentTimeMillis()
        _isScreenVisible.value = false
    }

    fun onSlotSettled(adOrdinal: Int) {
        if (!_isScreenVisible.value || !nativeListAdsEnabled.value) return
        nativeAdManager.onSlotSettled(adOrdinal)
    }

    fun requestAdForSlot(adOrdinal: Int) {
        onSlotSettled(adOrdinal)
    }

    fun onAdViewBound(ad: NativeAd) {
        nativeAdManager.onAdViewBound(ad)
    }

    fun onAdViewReleased(ad: NativeAd) {
        nativeAdManager.onAdViewReleased(ad)
    }

    override fun onCleared() {
        super.onCleared()
        nativeAdManager.destroyPool()
    }

    fun selectCategory(category: MessageCategory) {
        _selectedCategory.value = category
    }

    val roleStatus: StateFlow<SmsRoleStatus> = roleStateRepository.state

    fun requestDefaultSms(activity: Activity) {
        roleStateRepository.request(activity)
    }

    fun markRead(threadId: String) {
        viewModelScope.launch { repository.markRead(threadId) }
    }

    fun setPinned(threadId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(threadId, pinned) }
    }

    fun archive(threadId: String) {
        viewModelScope.launch { repository.setArchived(threadId, true) }
    }

    fun unarchive(threadId: String) {
        viewModelScope.launch { repository.setArchived(threadId, false) }
    }

    fun setMuted(threadId: String, muted: Boolean) {
        viewModelScope.launch { repository.setMuted(threadId, muted) }
    }

    fun setStarred(threadId: String, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(threadId, starred) }
    }

    fun delete(threadId: String) {
        viewModelScope.launch { repository.delete(threadId) }
    }

    fun block(address: String) {
        viewModelScope.launch { blockingRepository.block(address) }
    }
}

private fun Conversation.isOtp(): Boolean {
    val keywords = listOf("otp", "code", "one-time", "verification", "verification code", "verificationcode")
    val text = snippet.lowercase()
    return keywords.any { text.contains(it) }
}

private fun Conversation.isTransaction(): Boolean {
    val addr = primaryAddress.trim()
    if (addr.isEmpty()) return false
    val hasLetters = addr.any { it.isLetter() }
    if (hasLetters) return true
    val allDigits = addr.all { it.isDigit() }
    if (allDigits && addr.length < 8) return true
    return false
}

private fun Conversation.matchesCategory(category: MessageCategory): Boolean {
    return when (category) {
        MessageCategory.All -> !isArchived
        MessageCategory.OTP -> !isArchived && isOtp()
        MessageCategory.Transactions -> !isArchived && isTransaction() && !isOtp()
        MessageCategory.Personal -> !isArchived && !isTransaction() && !isOtp()
        MessageCategory.Unread -> !isArchived && isUnread
        MessageCategory.Archived -> isArchived
    }
}
