package app.messages.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import app.messages.Controller
import app.messages.Controller.Companion.adsModel
import app.messages.analytics.AnalyticsHelper.logAds
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/*class InterAdLoader {
    var isInterstitialLoading: Boolean = false
    var isInterstitialShowing: Boolean = false
    private var interstitialAd: InterstitialAd? = null

    fun loadInterstitialAds(activity: Activity) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            return
        }
        if (adsModel!!.isShowInterstitialAd.equals("Yes", ignoreCase = true)) {
            loadInterstitialAd(activity)
        }
    }

    private fun loadInterstitialAd(activity: Activity) {
        if (interstitialAd == null && !isInterstitialLoading) {
            isInterstitialLoading = true
            val adRequest = AdRequest.Builder().build()
            logAds(activity, "inter_req")
            InterstitialAd.load(
                activity,
                adsModel!!.interAdId!!,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        this@InterAdLoader.interstitialAd = interstitialAd
                        logAds(activity, "inter_loaded")
                        resetFailedCountInterstitial()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        increaseFailedCountInterstitial()
                        logAds(
                            activity,
                            "inter_failed_to_load_" + loadAdError.code
                        )
                        interstitialAd = null
                        isInterstitialLoading = false
                    }
                })
        }

    }

    fun showInterstitialAd(
        activity: Activity,
        isFromBack: Boolean,
        listener: FullScreenDismissListener
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            listener.onDismiss()
            return
        }
        if (adsModel!!.isShowInterstitialAd.equals("Yes", ignoreCase = true)) {

            val interstitialBackwardAdCount = 3

            val currentInterval: Int =
                if (isFromBack) interstitialBackwardCount else interstitialForwardCount
            val regularInterval =
                if (isFromBack) interstitialBackwardAdCount else adsModel!!.interstitialForwardAdCount

            if (currentInterval == regularInterval) {
                if (interstitialAd != null) {
                    interstitialAd!!.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                if (isFromBack) {
                                    resetInterstitialBackwardCount()
                                } else {
                                    resetInterstitialForwardCount()
                                }
                                interstitialAd = null
                                isInterstitialLoading = false
                                isInterstitialShowing = false
                                loadInterstitialAd(activity)
                                logAds(activity, "inter_dismissed")
                                listener.onDismiss()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                interstitialAd = null
                                isInterstitialLoading = false
                                isInterstitialShowing = false
                                logAds(activity, "inter_failed_to_show_" + adError.code)
                                loadInterstitialAd(activity)
                                listener.onDismiss()
                            }

                            override fun onAdShowedFullScreenContent() {
                                interstitialAd = null
                                isInterstitialShowing = true
                                logAds(activity, "inter_showed")
                            }

                            override fun onAdClicked() {
                                super.onAdClicked()
                                logAds(activity, "inter_clicked")
                            }
                        }
                    interstitialAd!!.show(activity)
                } else {
                    loadInterstitialAd(activity)
                    listener.onDismiss()
                }
            } else {
                if (isFromBack) {
                    increaseInterstitialBackwardCount()
                } else {
                    increaseInterstitialForwardCount()
                }
                listener.onDismiss()
            }
        } else {
            listener.onDismiss()
        }
    }


    fun interface FullScreenDismissListener {
        fun onDismiss()
    }

    companion object {
        private const val KEY_INTERSTITIAL_BACKWARD_COUNT = "interstitial_backward_count"
        private const val KEY_INTERSTITIAL_FORWARD_COUNT = "interstitial_forward_count"
        private const val KEY_FAILED_COUNT_INTERSTITIAL = "KeyFailedCountInterstitial"

        var instance: InterAdLoader? = null
            get() {
                if (field == null) {
                    field = InterAdLoader()
                }
                return field
            }
            private set

        fun resetInterstitialForwardCount() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_INTERSTITIAL_FORWARD_COUNT, 1).apply()
        }

        fun resetInterstitialBackwardCount() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_INTERSTITIAL_BACKWARD_COUNT, 1).apply()
        }

        fun resetCounter() {
            resetInterstitialForwardCount()
            resetInterstitialBackwardCount()
            resetFailedCountInterstitial()
        }

        private fun increaseInterstitialForwardCount() {
            val preferences: SharedPreferences = preference
            preferences.edit()
                .putInt(KEY_INTERSTITIAL_FORWARD_COUNT, interstitialForwardCount + 1).apply()
        }

        private fun increaseInterstitialBackwardCount() {
            val preferences: SharedPreferences = preference
            preferences.edit()
                .putInt(KEY_INTERSTITIAL_BACKWARD_COUNT, interstitialBackwardCount + 1)
                .apply()
        }


        private val interstitialForwardCount: Int
            get() {
                val preferences: SharedPreferences = preference
                return preferences.getInt(
                    KEY_INTERSTITIAL_FORWARD_COUNT, 1
                )
            }

        private val interstitialBackwardCount: Int
            get() {
                val preferences: SharedPreferences = preference
                return preferences.getInt(
                    KEY_INTERSTITIAL_BACKWARD_COUNT, 1
                )
            }

        private val preference: SharedPreferences
            get() = Controller.getInstance().getSharedPreferences(
                Controller.getInstance().packageName, Context.MODE_PRIVATE
            )

        fun resetFailedCountInterstitial() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_INTERSTITIAL, 0).apply()
        }

        private fun increaseFailedCountInterstitial() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_INTERSTITIAL, failedCountInterstitial + 1)
                .apply()
        }

        private val failedCountInterstitial: Int
            get() {
                val preferences: SharedPreferences = preference
                return preferences.getInt(KEY_FAILED_COUNT_INTERSTITIAL, 0)
            }


        fun startActivityWithAd(activity: Activity, intent: Intent?) {
            instance!!.showInterstitialAd(activity, false, FullScreenDismissListener {
                activity.startActivity(intent)
            })
        }

        fun startActivityWithAdLauncher(
            activity: Activity,
            intent: Intent?,
            activityResultLauncher: ActivityResultLauncher<Intent>
        ) {
            instance!!.showInterstitialAd(activity, false) {
                activityResultLauncher.launch(intent!!)
            }
        }

        fun finishWithAd(activity: Activity) {
            instance!!.showInterstitialAd(
                activity, true, FullScreenDismissListener { activity.finish() })
        }

        fun finishWithResultAfterAd(
            activity: Activity, resultKey: String, resultValue: String
        ) {
            instance?.showInterstitialAd(activity, true, FullScreenDismissListener {
                val resultIntent = Intent().apply {
                    putExtra(resultKey, resultValue)
                }
                activity.setResult(Activity.RESULT_OK, resultIntent)
                activity.finish()
            })
        }

    }

}*/
