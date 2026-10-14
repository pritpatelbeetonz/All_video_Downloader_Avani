package app.messages.monetization.ads

import android.app.Activity
import app.messages.di.ApplicationScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class InterstitialAdManager @Inject constructor(
    private val adsController: AdsController,
    private val remoteConfig: AdsRemoteConfig,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var ad: InterstitialAd? = null
    private var loading = false
    private var showWhenLoaded: Activity? = null
    private var shownThisProcess = false

    fun preload() {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            app.messages.Controller.getInstance(),
            remoteConfig.interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    val act = showWhenLoaded
                    showWhenLoaded = null
                    if (act != null) present(act, loaded) else ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    showWhenLoaded = null
                }
            },
        )
    }

    fun maybeShowOnColdStart(activity: Activity) {
        if (shownThisProcess) return
        scope.launch {
            if (!adsController.splashInterstitialAllowed(System.currentTimeMillis())) return@launch
            shownThisProcess = true
            withContext(Dispatchers.Main) {
                val cached = ad
                when {
                    cached != null -> { ad = null; present(activity, cached) }
                    !loading -> { showWhenLoaded = activity; preload() }
                    else -> showWhenLoaded = activity
                }
            }
        }
    }

    private fun present(activity: Activity, loaded: InterstitialAd) {
        if (activity.isFinishing || activity.isDestroyed) return
        loaded.show(activity)
        scope.launch { adsController.recordAppOpenShown(System.currentTimeMillis()) }
        preload()
    }
}
