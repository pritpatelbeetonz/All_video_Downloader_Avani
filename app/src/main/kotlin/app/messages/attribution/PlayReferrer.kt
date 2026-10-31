package app.messages.attribution

import android.content.Context
import android.os.Build
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import kotlin.coroutines.resume

/**
 * The Play Install Referrer: the text Play kept from the link the user installed through.
 * Read once and shared by the Google and Unity checks (and the Facebook referrer fallback).
 *
 *   Google ad:  "gclid=Cj0K...&utm_source=google&utm_medium=cpc"
 *   Organic:    "utm_source=google-play&utm_medium=organic"
 */
internal data class PlayReferrer(
    val status: Status,
    val raw: String? = null,
    /** When the user tapped the link (seconds). */
    val clickTs: Long = 0,
    /** When the install began (seconds). */
    val installTs: Long = 0,
    /** com.android.vending = Play Store. */
    val installer: String? = null,
) {
    enum class Status {
        /** Read fine. */
        OK,
        /** Not installed from Play (APK sideload, device without Play). */
        NOT_SUPPORTED,
        /** Could not be read (Play service down, timeout). */
        UNKNOWN,
    }

    val isOk get() = status == Status.OK

    /** The referrer split into parameters, keys lowercased: {"gclid": "Cj0K", "utm_source": "google"}. */
    val params: Map<String, String> by lazy { parseParams(raw) }

    companion object {

        suspend fun read(context: Context, timeoutSec: Int): PlayReferrer {
            AttributionLog.d("📥 Reading Play Install Referrer (timeout ${timeoutSec}s)...")
            val result = withTimeoutOrNull(timeoutSec * 1000L) { readNow(context) }
                ?: PlayReferrer(Status.UNKNOWN).also { AttributionLog.w("⏱️ Play referrer timed out") }
            when (result.status) {
                Status.OK -> AttributionLog.d(
                    "✅ Play referrer: \"${result.raw}\" | click=${result.clickTs} install=${result.installTs} installer=${result.installer}"
                )
                Status.NOT_SUPPORTED -> AttributionLog.d("🚫 Play referrer not supported (not a Play install). installer=${result.installer}")
                Status.UNKNOWN -> AttributionLog.w("❓ Play referrer unavailable")
            }
            return result
        }

        private suspend fun readNow(context: Context): PlayReferrer = suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context).build()
            fun reply(result: PlayReferrer) {
                if (cont.isActive) cont.resume(result)
                runCatching { client.endConnection() }
            }
            cont.invokeOnCancellation { runCatching { client.endConnection() } }
            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(code: Int) {
                        when (code) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> try {
                                val d = client.installReferrer
                                reply(
                                    PlayReferrer(
                                        Status.OK,
                                        d.installReferrer,
                                        d.referrerClickTimestampSeconds,
                                        d.installBeginTimestampSeconds,
                                        installerPackage(context),
                                    )
                                )
                            } catch (e: Exception) {
                                reply(PlayReferrer(Status.UNKNOWN))
                            }
                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED ->
                                reply(PlayReferrer(Status.NOT_SUPPORTED, installer = installerPackage(context)))
                            else -> reply(PlayReferrer(Status.UNKNOWN))
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() = reply(PlayReferrer(Status.UNKNOWN))
                })
            } catch (e: Exception) {
                reply(PlayReferrer(Status.UNKNOWN))
            }
        }

        /**
         * "a=1&B=2" -> {"a": "1", "b": "2"}. Handles a double-encoded referrer ("gclid%3D...",
         * where %3D is "=" and %26 is "&") by decoding it once first. Never throws.
         */
        fun parseParams(referrer: String?): Map<String, String> {
            if (referrer.isNullOrEmpty()) return emptyMap()
            var raw: String = referrer
            if (!raw.contains('=') && raw.lowercase().contains("%3d")) {
                raw = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            }
            return try {
                raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
                    val i = pair.indexOf('=')
                    val key = if (i < 0) pair else pair.substring(0, i)
                    val value = if (i < 0) "" else pair.substring(i + 1)
                    URLDecoder.decode(key, "UTF-8").lowercase() to URLDecoder.decode(value, "UTF-8")
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        private fun installerPackage(context: Context): String? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
    }
}
