package app.messages.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The build doc (§8) explicitly calls for a backup encrypt/decrypt round-trip
 * test. AES-256-GCM is authenticated, so wrong key / tamper must fail closed.
 */
class BackupCryptoTest {

    private val pass = "correct horse battery".toCharArray()

    @Test fun `round-trips arbitrary content`() {
        val data = "messages.json: hello é 😀 ".repeat(500).toByteArray()
        val restored = BackupCrypto.decrypt(BackupCrypto.encrypt(data, pass), pass)
        assertArrayEquals(data, restored)
    }

    @Test fun `round-trips empty content`() {
        val restored = BackupCrypto.decrypt(BackupCrypto.encrypt(ByteArray(0), pass), pass)
        assertArrayEquals(ByteArray(0), restored)
    }

    @Test fun `wrong passphrase fails closed`() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), pass)
        assertTrue(throws { BackupCrypto.decrypt(blob, "wrong passphrase".toCharArray()) })
    }

    @Test fun `tampered ciphertext fails closed`() {
        val blob = BackupCrypto.encrypt("secret".toByteArray(), pass)
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte() // flip a tag byte
        assertTrue(throws { BackupCrypto.decrypt(blob, pass) })
    }

    @Test fun `same plaintext encrypts differently each time`() {
        val data = "same".toByteArray()
        val a = BackupCrypto.encrypt(data, pass)
        val b = BackupCrypto.encrypt(data, pass)
        assertFalse(a.contentEquals(b)) // random salt + IV
    }

    private inline fun throws(block: () -> Unit): Boolean =
        try { block(); false } catch (e: Exception) { true }
}
