package app.messages.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON (de)serialization for the backup schema. `ignoreUnknownKeys` keeps forward
 * compatibility — a newer backup with extra fields still restores on an older app.
 */
object BackupSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodeManifest(manifest: BackupManifest): String = json.encodeToString(manifest)
    fun decodeManifest(text: String): BackupManifest = json.decodeFromString(text)

    fun encodeMessages(messages: List<BackupMessage>): String = json.encodeToString(messages)
    fun decodeMessages(text: String): List<BackupMessage> = json.decodeFromString(text)
}
