package app.messages.attribution

import android.content.Context
import com.facebook.FacebookSdk
import com.facebook.applinks.AppLinkData
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Meta (Facebook / Instagram) ad install. Facebook ads don't add a gclid, so two signals are used:
 *   1. Deferred deep link: the link attached to the ad, which the Facebook SDK returns on the first
 *      launch (e.g. "myapp://open?utm_source=facebook&fbclid=..."). Matched against fbUtmsForDeferrerLink.
 *   2. Play referrer: Meta's install link leaves its own values in it (e.g. "utm_source=apps.facebook.com").
 *      Matched against fbUtm.
 *
 * The SDK is started elsewhere (MetaAudienceNetworkManager, with the app id from the ads Remote
 * Config). Without that app id this check can only use the Play referrer.
 */
internal object FacebookSourceDetector {

    /** What matched ("deferred_link" or "referrer"), or null when this isn't a Facebook install. */
    suspend fun detect(context: Context, referrer: PlayReferrer, config: AttributionConfig): String? {
        AttributionLog.d("🟣 [Facebook] Checking...")

        // 1. Deferred deep link
        AttributionLog.d("🟣 [Facebook] Deferred link markers from Remote Config (fbUtmsForDeferrerLink): ${config.fbUtmsForDeferrerLink}")
        val link = fetchDeferredLink(context, config)
        if (!link.isNullOrEmpty()) {
            AttributionLog.d("🟣 [Facebook] Deferred link: $link")
            containsMarker(link, config.fbUtmsForDeferrerLink)?.let {
                AttributionLog.d("🟣 [Facebook] ✅ Deferred link contains \"$it\"")
                return "deferred_link"
            }
            AttributionLog.d("🟣 [Facebook] ❌ Deferred link has no marker from fbUtmsForDeferrerLink: ${config.fbUtmsForDeferrerLink}")
            log(context, "src_fb_deferred_link_not_matched")
        } else {
            log(context, "src_fb_deferred_link_not_found")
        }

        // 2. Play referrer
        AttributionLog.d("🟣 [Facebook] Referrer markers from Remote Config (fbUtm): ${config.fbUtm}")
        if (referrer.isOk) {
            containsMarker(referrer.raw.orEmpty(), config.fbUtm)?.let {
                AttributionLog.d("🟣 [Facebook] ✅ Play referrer contains \"$it\"")
                return "referrer"
            }
            AttributionLog.d("🟣 [Facebook] ❌ Play referrer has no marker from fbUtm ${config.fbUtm}")
            log(context, "src_fb_referrer_not_match")
        }

        AttributionLog.d("🟣 [Facebook] ❌ No match")
        return null
    }

    /** Fire-and-forget attribution diagnostic event (reference parity: src_fb_* events). */
    private fun log(context: Context, event: String) =
        FirebaseAnalytics.getInstance(context).logEvent(event, null)

    // The SDK may still be starting on the first launch, so this waits and retries.
    private suspend fun fetchDeferredLink(context: Context, config: AttributionConfig): String? {
        repeat(config.fbDeepLinkTries) { attempt ->
            delay(config.fbAwaitSecondsBetweenTries * 1000L)
            if (!FacebookSdk.isInitialized()) {
                AttributionLog.d("🟣 [Facebook] ⏳ SDK not started yet (try ${attempt + 1}/${config.fbDeepLinkTries})")
                return@repeat
            }
            val link = try {
                withTimeoutOrNull(config.fbDeferredLinkTimeoutSec * 1000L) {
                    suspendCancellableCoroutine<String?> { cont ->
                        AppLinkData.fetchDeferredAppLinkData(context) { data ->
                            if (cont.isActive) {
                                val uri = data?.targetUri?.toString()
                                val ref = data?.ref
                                if (data != null) {
                                    AttributionLog.d("🟣 [Facebook] 📩 AppLinkData received: targetUri=$uri, ref=$ref")
                                }
                                cont.resume(uri ?: ref)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AttributionLog.w("🟣 [Facebook] ⚠️ Deferred link failed", e)
                log(context, "src_fb_deferred_link_failed")
                null
            }
            if (link != null) return link
            AttributionLog.d("🟣 [Facebook] No deferred link (try ${attempt + 1}/${config.fbDeepLinkTries})")
        }
        return null
    }
}
