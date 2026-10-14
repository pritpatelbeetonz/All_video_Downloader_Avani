package app.messages.data.backup

import kotlinx.serialization.Serializable

/**
 * Canonical backup schema (§7.3 "messages.json (canonical schema)"). Versioned
 * so a future app can restore an older backup. SMS-only in v1; MMS media backup
 * (the `media/` folder) is a later addition that bumps [BackupManifest.schemaVersion].
 */
@Serializable
data class BackupManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAtMs: Long,
    val appVersionName: String,
    val messageCount: Int,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupMessage(
    val address: String,
    val body: String,
    val dateMs: Long,
    val outgoing: Boolean,
    val read: Boolean,
)

@Serializable
data class BackupDocument(
    val manifest: BackupManifest,
    val messages: List<BackupMessage>,
)
