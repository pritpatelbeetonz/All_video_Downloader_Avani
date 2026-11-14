package app.messages.monetization.ads

import android.content.Context
import android.os.Bundle
import android.util.Log
import app.messages.config.AdsConfigManager
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger

/**
 * Reports AdMob ad-revenue to Meta (Facebook) via the Facebook SDK's [AppEventsLogger], so Meta can
 * optimize campaigns on in-app ad revenue. We send for ALL users and let Meta attribute the ones it
 * can match to a Facebook campaign.
 *
 * Gated by:
 *  - [AdsConfigManager.shouldSendMetaAdRevenue] — master flag `meta_ad_revenue_on` AND the SEPARATE
 *    Meta allowlist `meta_ad_impression_allowed` (independent of the Firebase `adImpressionEventAllowed`).
 *  - [FacebookSdk.isInitialized] — the FB app id must already be configured (see MetaAudienceNetworkManager);
 *    otherwise there is nothing to report to and we skip silently.
 *
 * Called from the single revenue choke point (Controller.sendRevenueToAnalytics).
 *
 * Logcat: tag:MetaRevenue
 */
object MetaRevenueReporter {

    private const val TAG = "MetaRevenue"

    fun logAdRevenue(
        context: Context,
        adUnitId: String?,
        revenue: Double,
        currency: String?,
        placement: String,
    ) {
        if (!AdsConfigManager.shouldSendMetaAdRevenue(adUnitId)) return
        if (!FacebookSdk.isInitialized()) {
            Log.d(TAG, "skip [$placement] — Facebook SDK not initialized (no app id)")
            return
        }
        try {
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currency?.takeIf { it.isNotBlank() } ?: "USD")
                putString("ad_platform", "admob")
                putString("ad_placement", placement)
                putString("ad_unit", adUnitId)
            }
            AppEventsLogger.newLogger(context.applicationContext).logEvent("ad_impression", revenue, params)
            Log.d(TAG, "sent ad_impression rev=$revenue ${currency ?: "USD"} unit=$adUnitId [$placement]")
        } catch (t: Throwable) {
            Log.w(TAG, "logAdRevenue failed [$placement]: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
