package app.messages.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.data.telephony.MessageRepository
import app.messages.data.telephony.SearchRepository
import app.messages.data.telephony.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<SearchResults> = _query
        .debounce(300)
        .flatMapLatest { q ->
            flow { emit(repository.search(q)) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SearchResults(emptyList(), emptyList()),
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Resolves (creating if needed) the thread for a tapped contact result. */
    fun openContact(address: String, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            val threadId = withContext(Dispatchers.IO) {
                messageRepository.resolveThreadId(address)
            }
            if (threadId.isNotBlank()) onResolved(threadId)
        }
    }
}
