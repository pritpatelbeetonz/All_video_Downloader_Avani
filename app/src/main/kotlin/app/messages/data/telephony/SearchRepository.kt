package app.messages.data.telephony

import app.messages.data.contacts.ContactSuggestion
import app.messages.data.contacts.ContactsResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResults(
    val contacts: List<ContactSuggestion>,
    val messages: List<MessageSearchResult>,
) {
    val isEmpty: Boolean get() = contacts.isEmpty() && messages.isEmpty()
}

interface SearchRepository {
    suspend fun search(query: String): SearchResults
}

/**
 * M2 search backs onto the Telephony provider directly (debounced `LIKE`).
 *
 * Per build doc §7.3 the eventual design mirrors messages into a Room FTS4/5
 * index for instant search over large mailboxes; that index + its sync is a
 * standalone follow-up. Provider `LIKE` is correct and honest against real
 * data, just O(n) — fine until a user has tens of thousands of messages.
 */
@Singleton
class TelephonySearchRepository @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
    private val query: ConversationQuery,
    private val contacts: ContactsResolver,
) : SearchRepository {
    override suspend fun search(queryText: String): SearchResults =
        withContext(Dispatchers.IO) {
            val q = queryText.trim()
            if (q.length < 2) return@withContext SearchResults(emptyList(), emptyList())
            val rawMessages = runCatching { query.searchMessages(q, limit = 50) }
                .getOrDefault(emptyList())
            SearchResults(
                contacts = contacts.search(q, limit = 8),
                // Resolve sender names so results read "Mom", not "+9184...".
                messages = rawMessages.map {
                    it.copy(contactName = contacts.resolve(it.address).displayName)
                },
            )
        }
}
