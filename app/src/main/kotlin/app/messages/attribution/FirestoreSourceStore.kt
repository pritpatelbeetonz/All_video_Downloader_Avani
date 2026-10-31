package app.messages.attribution

import android.content.Context
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps an ad source across reinstalls on the same phone. Prefs are wiped on uninstall, so a
 * decided ad source is also written to Firestore under a hashed device key:
 *
 *   install_referrers/<sha256(ANDROID_ID)>  =  { src: "google", t: <server time>, id: <same key> }
 *
 * On a reinstall the document is found before the referrer checks run, and its source is reused.
 * Only google / facebook / unity are written; regular users are never stored.
 *
 * The key is a SHA-256 hash, never the raw ANDROID_ID (falls back to the advertising id).
 */
internal object FirestoreSourceStore {

    private const val COLLECTION = "install_referrers"
    private const val ZERO_AD_ID = "00000000-0000-0000-0000-000000000000"

    private val referrers get() = FirebaseFirestore.getInstance().collection(COLLECTION)

    /** sha256 of ANDROID_ID, else of the advertising id; null when neither is usable. */
    suspend fun deviceKey(context: Context): String? = withContext(Dispatchers.IO) {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (isValidId(androidId)) return@withContext sha256(androidId!!)
        val adId = runCatching { AdvertisingIdClient.getAdvertisingIdInfo(context).id }.getOrNull()
        if (isValidId(adId) && adId != ZERO_AD_ID) sha256(adId!!) else null
    }

    /**
     * The ad source saved for this device by an earlier install, or null (none saved, or regular).
     * Throws on a failed or timed-out read, so the caller can log it and carry on with the checks.
     */
    suspend fun read(deviceKey: String, timeoutSec: Int): String? {
        AttributionLog.d("🔥 [Firestore] Looking up $COLLECTION/${deviceKey.take(12)}...")
        val doc = withTimeout(timeoutSec * 1000L) { referrers.document(deviceKey).get().await() }
        val src = doc.getString("src")
        AttributionLog.d(
            if (doc.exists()) "🔥 [Firestore] Found src=$src" else "🔥 [Firestore] No document (first install on this phone)"
        )
        return src
    }

    /** Writes the ad source; [onDone] gets true once the server has it. */
    fun write(deviceKey: String, source: String, onDone: (Boolean) -> Unit) {
        AttributionLog.d("🔥 [Firestore] Saving src=$source...")
        referrers.document(deviceKey)
            .set(mapOf("src" to source, "t" to FieldValue.serverTimestamp(), "id" to deviceKey), SetOptions.merge())
            .addOnSuccessListener {
                AttributionLog.d("🔥 [Firestore] ✅ Saved src=$source")
                onDone(true)
            }
            .addOnFailureListener {
                AttributionLog.w("🔥 [Firestore] ⚠️ Save failed (retried next launch)", it)
                onDone(false)
            }
    }

    private fun isValidId(id: String?) = !id.isNullOrEmpty() && id != "unknown"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: Exception("Task failed"))
        }
    }
}
