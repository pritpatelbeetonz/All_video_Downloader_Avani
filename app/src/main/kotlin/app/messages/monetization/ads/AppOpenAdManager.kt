package app.messages.monetization.ads

import android.app.Activity
import app.messages.core.ads.AdEntryPoint
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor(
    private val adsController: AdsController,
    private val remoteConfig: AdsRemoteConfig,
) {
    private var ad: AppOpenAd? = null
    private var loading = false

    fun preload() {
        if (ad != null || loading) return
        loading = true
        AppOpenAd.load(
            app.messages.Controller.getInstance(),
            remoteConfig.appOpenUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            },
        )
    }

    suspend fun maybeShow(activity: Activity, entryPoint: AdEntryPoint) {
        val decision = adsController.appOpenDecision(entryPoint, System.currentTimeMillis())
        if (!decision.allowed) return
        val loaded = ad ?: run { preload(); return }
        loaded.show(activity)
        adsController.recordAppOpenShown(System.currentTimeMillis())
        ad = null
        preload()
    }
}
