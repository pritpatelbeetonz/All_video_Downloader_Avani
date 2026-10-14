package app.messages.data.blocking

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Number blocking via [BlockedNumberContract] (§F9). Reads/writes here require
 * the app to be the default SMS or dialer app — we're the default SMS handler,
 * so the platform grants access. Every call is wrapped: if the role is lost the
 * provider throws `SecurityException` and we degrade to "nothing blocked".
 */
@Singleton
class BlockingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _blocked = MutableStateFlow<List<String>>(emptyList())
    val blocked: StateFlow<List<String>> = _blocked.asStateFlow()

    init {
        refreshBlocking()
    }

    fun isBlocked(number: String): Boolean = runCatching {
        BlockedNumberContract.isBlocked(context, number)
    }.getOrDefault(false)

    suspend fun block(number: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                }
                context.contentResolver.insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI, values,
                )
            }
            loadBlocked()
        }
    }

    suspend fun unblock(number: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                    arrayOf(number),
                )
            }
            loadBlocked()
        }
    }

    private fun refreshBlocking() {
        runCatching { loadBlocked() }
    }

    private fun loadBlocked() {
        val out = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    c.getString(0)?.takeIf { it.isNotBlank() }?.let(out::add)
                }
            }
        }
        _blocked.value = out
    }
}
