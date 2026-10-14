package app.messages.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSerializerTest {

    private val messages = listOf(
        BackupMessage(address = "+15551112222", body = "Hello \"there\", commas, and\nnewlines", dateMs = 1_700_000_000_000L, outgoing = false, read = true),
        BackupMessage(address = "Bank", body = "OTP 123456", dateMs = 1_700_000_500_000L, outgoing = true, read = true),
    )

    @Test fun `messages round-trip through json`() {
        val restored = BackupSerializer.decodeMessages(BackupSerializer.encodeMessages(messages))
        assertEquals(messages, restored)
    }

    @Test fun `manifest round-trips through json`() {
        val manifest = BackupManifest(createdAtMs = 1L, appVersionName = "1.0.0", messageCount = 2)
        val restored = BackupSerializer.decodeManifest(BackupSerializer.encodeManifest(manifest))
        assertEquals(manifest, restored)
    }

    /** The full pipeline the backup file uses: serialize → encrypt → decrypt → parse. */
    @Test fun `serialize-encrypt-decrypt-deserialize preserves messages`() {
        val pass = "vault key".toCharArray()
        val json = BackupSerializer.encodeMessages(messages)
        val blob = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), pass)
        val plain = String(BackupCrypto.decrypt(blob, pass), Charsets.UTF_8)
        assertEquals(messages, BackupSerializer.decodeMessages(plain))
    }
}
