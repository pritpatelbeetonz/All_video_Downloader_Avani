package app.messages.data.contacts

import android.content.Context
import android.provider.ContactsContract
import app.messages.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Resolved contact info for a phone number. */
data class ContactInfo(
    val displayName: String?,
    val photoUri: String?,
) {
    companion object {
        val EMPTY = ContactInfo(null, null)
    }
}

/** A contact match for the compose recipient autocomplete. */
data class ContactSuggestion(
    val name: String,
    val number: String,
    val photoUri: String?,
)

/**
 * Resolves phone numbers to contact name + photo.
 *
 * The whole phone book is read once into an in-memory index keyed by the trailing
 * digits of each number, and [resolve] answers from that index. The previous
 * implementation fired one async [ContactsContract.PhoneLookup] per address and
 * emitted [updates] on every completion — and because the conversation repository
 * re-runs its full telephony query on each of those, a mailbox with N distinct
 * senders cost N full re-queries before the list ever settled. One bulk read means
 * one query and exactly one update.
 *
 * Matching on the trailing digits absorbs the formats PhoneLookup used to handle
 * for us (+91-prefixed vs 0-prefixed vs spaced). Short codes / alphanumeric
 * senders ("VM-HDFCBK") have no usable digits and resolve to [ContactInfo.EMPTY];
 * the UI falls back to the raw address.
 */
@Singleton
class ContactsResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val cache = ConcurrentHashMap<String, ContactInfo>()

    /** Trailing-digit key → contact. Empty until the bulk load lands. */
    @Volatile
    private var directory: Map<String, ContactInfo> = emptyMap()

    @Volatile
    private var directoryLoaded = false
    private val directoryLoading = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    fun resolve(address: String): ContactInfo {
        if (address.isBlank()) return ContactInfo.EMPTY
        cache[address]?.let { return it }

        if (!directoryLoaded) {
            ensureDirectoryLoaded()
            // Don't cache the miss — the directory hasn't been read yet, and the
            // single update emitted when it lands re-resolves everything.
            return ContactInfo.EMPTY
        }

        val resolved = directoryKey(address)?.let { directory[it] } ?: ContactInfo.EMPTY
        cache[address] = resolved
        return resolved
    }

    /** Starts the one-shot phone-book read if it hasn't run yet. */
    fun ensureDirectoryLoaded() {
        if (directoryLoaded || !directoryLoading.compareAndSet(false, true)) return
        appScope.launch(Dispatchers.IO) {
            try {
                directory = loadDirectory()
            } catch (_: Exception) {
                directory = emptyMap()
            } finally {
                directoryLoaded = true
                directoryLoading.set(false)
            }
            _updates.tryEmit(Unit)
        }
    }

    /** Drops the cache so a contacts-change observer can force re-resolution. */
    fun invalidate() {
        cache.clear()
        directoryLoaded = false
        directory = emptyMap()
        ensureDirectoryLoaded()
    }

    /**
     * Searches contacts by name or number for the compose autocomplete.
     * Returns up to [limit] matches, de-duplicated by number.
     */
    fun search(rawQuery: String, limit: Int = 20): List<ContactSuggestion> {
        val q = rawQuery.trim()
        if (q.isBlank()) return emptyList()
        return runCatching { searchInternal(q, limit) }.getOrDefault(emptyList())
    }

    private fun searchInternal(q: String, limit: Int): List<ContactSuggestion> {
        val like = "%$q%"
        val out = LinkedHashMap<String, ContactSuggestion>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf(like, like),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val normalized = number.replace(Regex("""[\s\-()]"""), "")
                out.putIfAbsent(
                    normalized,
                    ContactSuggestion(name, number, c.getString(2)),
                )
            }
        }
        return out.values.toList()
    }

    /** One pass over the phone book, indexed by [directoryKey]. */
    private fun loadDirectory(): Map<String, ContactInfo> {
        val out = HashMap<String, ContactInfo>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ),
            null, null, null,
        )?.use { c ->
            val idxNumber = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idxName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val idxPhoto = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (c.moveToNext()) {
                val key = directoryKey(c.getString(idxNumber).orEmpty()) ?: continue
                val name = c.getString(idxName)?.takeIf { it.isNotBlank() }
                val photo = c.getString(idxPhoto)?.takeIf { it.isNotBlank() }
                if (name == null && photo == null) continue
                // First writer wins — contacts are returned in a stable order, and a
                // duplicate number across contacts is a data conflict either way.
                out.putIfAbsent(key, ContactInfo(displayName = name, photoUri = photo))
            }
        }
        return out
    }

    /**
     * Index key for a phone number: its trailing [MATCH_DIGITS] digits, so that
     * "+91 98765 43210", "098765 43210" and "9876543210" all collide. Returns null
     * for addresses without enough digits to match on (short codes, sender IDs).
     */
    private fun directoryKey(raw: String): String? {
        val digits = buildString(raw.length) {
            for (ch in raw) if (ch.isDigit()) append(ch)
        }
        if (digits.length < MATCH_DIGITS) return null
        return digits.takeLast(MATCH_DIGITS)
    }

    private companion object {
        /**
         * Long enough that two different local numbers don't collide, short enough
         * to ignore country/trunk prefixes. Matches the AOSP min-match default.
         */
        const val MATCH_DIGITS = 7
    }
}
