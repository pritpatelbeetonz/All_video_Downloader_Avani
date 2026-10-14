package app.messages.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a PBKDF2-derived key for encrypted backups (Play policy §2.5:
 * "Backups must be encrypted (AES-256, key derived from user passphrase…)").
 *
 * Blob layout: `[ salt(16) | iv(12) | ciphertext+tag ]`. GCM is authenticated, so
 * a wrong passphrase or any tampering throws on [decrypt] — the restore flow turns
 * that into a "wrong password / corrupt file" message rather than garbage data.
 *
 * Pure JVM crypto (PBKDF2WithHmacSHA1 is available since API 10, AES/GCM since
 * API 19) so the round-trip is unit-testable per build doc §8.
 */
object BackupCrypto {
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        return salt + iv + cipher.doFinal(plaintext)
    }

    /** @throws javax.crypto.AEADBadTagException on wrong passphrase or tampering. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > SALT_BYTES + IV_BYTES) { "Backup file is truncated" }
        val salt = blob.copyOfRange(0, SALT_BYTES)
        val iv = blob.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val body = blob.copyOfRange(SALT_BYTES + IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(body)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }
}
