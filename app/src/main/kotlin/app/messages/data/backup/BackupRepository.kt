package app.messages.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local encrypted backup + restore (§F14). Produces a single AES-256-GCM blob
 * that wraps a zip of `manifest.json` + `messages.json` (the canonical schema,
 * §7.3). SMS-only in v1; MMS media is a later schema bump.
 *
 * Free + manual to a user-picked SAF location. Drive auto-backup (Premium) is a
 * later addition that reuses [backupTo] against a Drive-provided stream.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class BackupResult(val messageCount: Int)
    data class RestoreResult(val imported: Int, val skipped: Int)

    suspend fun backupTo(uri: Uri, passphrase: CharArray): Result<BackupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val messages = readAllMessages()
                val manifest = BackupManifest(
                    createdAtMs = System.currentTimeMillis(),
                    appVersionName = app.messages.BuildConfig.VERSION_NAME,
                    messageCount = messages.size,
                )
                val blob = BackupCrypto.encrypt(zip(manifest, messages), passphrase)
                context.contentResolver.openOutputStream(uri)?.use { it.write(blob) }
                    ?: error("Couldn't open the backup file for writing")
                BackupResult(messages.size)
            }
        }

    suspend fun restoreFrom(uri: Uri, passphrase: CharArray): Result<RestoreResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Couldn't open the backup file")
                // Throws (AEADBadTagException) on a wrong passphrase or corrupt file.
                val zipBytes = BackupCrypto.decrypt(blob, passphrase)
                importMessages(readZipMessages(zipBytes))
            }
        }

    private fun readAllMessages(): List<BackupMessage> {
        val cols = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
            Telephony.Sms.TYPE, Telephony.Sms.READ,
        )
        val out = ArrayList<BackupMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, cols, null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val iT = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val iR = c.getColumnIndexOrThrow(Telephony.Sms.READ)
            while (c.moveToNext()) {
                val type = c.getInt(iT)
                out.add(
                    BackupMessage(
                        address = c.getString(iA) ?: "",
                        body = c.getString(iB) ?: "",
                        dateMs = c.getLong(iD),
                        outgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                        read = c.getInt(iR) == 1,
                    ),
                )
            }
        }
        return out
    }

    private fun zip(manifest: BackupManifest, messages: List<BackupMessage>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(BackupSerializer.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("messages.json"))
            zos.write(BackupSerializer.encodeMessages(messages).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun readZipMessages(zipBytes: ByteArray): List<BackupMessage> {
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "messages.json") {
                    return BackupSerializer.decodeMessages(zis.readBytes().toString(Charsets.UTF_8))
                }
                entry = zis.nextEntry
            }
        }
        error("Backup is missing messages.json")
    }

    private fun importMessages(messages: List<BackupMessage>): RestoreResult {
        var imported = 0
        var skipped = 0
        for (m in messages) {
            if (exists(m)) {
                skipped++
                continue
            }
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, m.address)
                put(Telephony.Sms.BODY, m.body)
                put(Telephony.Sms.DATE, m.dateMs)
                put(Telephony.Sms.READ, if (m.read) 1 else 0)
                put(
                    Telephony.Sms.TYPE,
                    if (m.outgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX,
                )
            }
            if (context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null) {
                imported++
            } else {
                skipped++
            }
        }
        return RestoreResult(imported, skipped)
    }

    /** Dedup guard so re-running a restore doesn't duplicate rows. */
    private fun exists(m: BackupMessage): Boolean {
        val sel = "${Telephony.Sms.DATE} = ? AND ${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?"
        val args = arrayOf(m.dateMs.toString(), m.address, m.body)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), sel, args, null,
        )?.use { return it.moveToFirst() }
        return false
    }
}
