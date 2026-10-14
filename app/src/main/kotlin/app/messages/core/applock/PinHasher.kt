package app.messages.core.applock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A stored PIN credential: a random salt + PBKDF2 hash, serialized as
 * "saltHex:hashHex". We never store the PIN itself (§3.5 privacy posture).
 */
data class PinCredential(val saltHex: String, val hashHex: String) {
    fun encode(): String = "$saltHex:$hashHex"

    companion object {
        fun decode(value: String): PinCredential? {
            val parts = value.split(":")
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
            return PinCredential(parts[0], parts[1])
        }
    }
}

/**
 * Salted PBKDF2 hashing for the app-lock PIN (§F16). Pure JVM crypto so it runs
 * in unit tests and on every supported API: PBKDF2WithHmacSHA1 ships since API
 * 10 and hex (not java.util.Base64, which is API 26+) keeps minSdk 24 happy.
 *
 * The real protection is that the credential lives in app-private DataStore; the
 * salt + iteration count raise the cost of an offline brute force if it leaks.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun create(pin: String): PinCredential {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return PinCredential(salt.toHex(), pbkdf2(pin, salt).toHex())
    }

    /** Constant-time comparison to avoid leaking match progress via timing. */
    fun verify(pin: String, credential: PinCredential): Boolean {
        val salt = credential.saltHex.hexToBytes()
        val expected = credential.hashHex.hexToBytes()
        return MessageDigest.isEqual(pbkdf2(pin, salt), expected)
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }
}

private const val HEX = "0123456789abcdef"

private fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(HEX[v ushr 4])
        sb.append(HEX[v and 0x0F])
    }
    return sb.toString()
}

private fun String.hexToBytes(): ByteArray {
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        out[i] = ((hi shl 4) + lo).toByte()
    }
    return out
}
