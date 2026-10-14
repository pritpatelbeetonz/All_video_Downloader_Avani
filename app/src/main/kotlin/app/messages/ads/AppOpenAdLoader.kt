package app.messages.ads

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.messages.Controller
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import app.messages.ads.listeners.AppOpenAdsListener
import app.messages.analytics.AnalyticsHelper
import app.messages.constant.Constants

import java.util.Date

/*
class AppOpenAdLoader(val controller: Controller) :
    LifecycleObserver, ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadCallback: AppOpenAdLoadCallback? = null
    var currentContactActivity: Activity? = null
    var contactAppOpenActivity: Activity? = null
    var loadTimeLong: Long = 0
    var TAG = "AppOpenAdsStatus"

    init {
        controller.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun preloadAppOpenAd(activity: Activity?) {
        if (activity != null &&
            app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            return
        }
        if (Controller.adsModel?.appOpenAdPreload
                .equals("Yes", ignoreCase = true)
        ) {
            contactAppOpenActivity = activity
            if (isAvailableAppOpenAd) {
                return
            }

            appOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_loaded")
                    this@AppOpenAdLoader.appOpenAd = appOpenAd
                    this@AppOpenAdLoader.loadTimeLong = Date().time

                    }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AnalyticsHelper.logAds(
                        contactAppOpenActivity!!,
                        "appopen_failed_" + loadAdError.code
                    )
                    }
            }

            AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_request")
            val requestAds = AdRequest.Builder().build()
            AppOpenAd.load(
                controller,
                Constants.AD_ID_APP_OPEN,
                requestAds,
                appOpenAdLoadCallback as AppOpenAdLoadCallback
            )
        }
    }

    val isAvailableAppOpenAd: Boolean
        get() = appOpenAd != null */
/*&& wasLoadTimeLessThanNHoursAgoAppOpenAdClick(4)*//*


    fun contactAppOpenAdsRequest(activity: Activity?) {
        */
/* if (Controller.adsModel?.isShowAppOpenAd
                 .equals("Yes", ignoreCase = true)
         ) {*//*

        if (!appOpenShowingBoolean && isAvailableAppOpenAd) {

        } else {
            preloadAppOpenAd(activity)
        }
//        }
    }

    fun showAppOpenAds(
        activity: Activity,
        onShowAdCompleteListener: AppOpenAdsListener
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            onShowAdCompleteListener.appOpenAdsShow()
            return
        }
        contactAppOpenActivity = activity

        */
/*if (Controller.adsModel?.isShowAppOpenAd
                .equals("Yes", ignoreCase = true)
        ) {*//*

            if (!appOpenShowingBoolean && isAvailableAppOpenAd) {
                val fullScreenContentCallback: FullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_dismissed")
                            this@AppOpenAdLoader.appOpenAd = null
                            appOpenShowingBoolean = false
                            preloadAppOpenAd(activity)
                            onShowAdCompleteListener.appOpenAdsShow()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            AnalyticsHelper.logAds(
                                contactAppOpenActivity!!,
                                "appopen_failed_" + adError.code
                            )
                            onShowAdCompleteListener.appOpenAdsShow()
                        }

                        override fun onAdShowedFullScreenContent() {
                            AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_showed")
                            appOpenShowingBoolean = true
                        }

                        override fun onAdClicked() {
                            super.onAdClicked()
                            AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_clicked")
                        }
                    }
                appOpenAd!!.fullScreenContentCallback = fullScreenContentCallback
                appOpenAd!!.show(activity)
            } else {
                appOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
                    override fun onAdLoaded(appOpenAd: AppOpenAd) {
                        AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_loaded")
                        this@AppOpenAdLoader.appOpenAd = appOpenAd
                        this@AppOpenAdLoader.loadTimeLong =
                            Date().time

                        val fullScreenContentCallback: FullScreenContentCallback =
                            object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    AnalyticsHelper.logAds(
                                        contactAppOpenActivity!!,
                                        "appopen_dismissed"
                                    )
                                    this@AppOpenAdLoader.appOpenAd = null
                                    appOpenShowingBoolean = false
                                    preloadAppOpenAd(activity)
                                    onShowAdCompleteListener.appOpenAdsShow()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    AnalyticsHelper.logAds(
                                        contactAppOpenActivity!!,
                                        "appopen_failed_" + adError.code
                                    )
                                    onShowAdCompleteListener.appOpenAdsShow()
                                }

                                override fun onAdShowedFullScreenContent() {
                                    AnalyticsHelper.logAds(
                                        contactAppOpenActivity!!,
                                        "appopen_showed"
                                    )
                                    appOpenShowingBoolean = true
                                }

                                override fun onAdClicked() {
                                    super.onAdClicked()
                                    AnalyticsHelper.logAds(
                                        contactAppOpenActivity!!,
                                        "appopen_clicked"
                                    )
                                }
                            }
                        this@AppOpenAdLoader.appOpenAd!!.fullScreenContentCallback =
                            fullScreenContentCallback
                        this@AppOpenAdLoader.appOpenAd!!.show(
                            currentContactActivity!!
                        )
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        AnalyticsHelper.logAds(
                            contactAppOpenActivity!!,
                            "appopen_failed_" + loadAdError.code
                        )
                        onShowAdCompleteListener.appOpenAdsShow()
                    }
                }
                AnalyticsHelper.logAds(contactAppOpenActivity!!, "appopen_request")
                val requestAds = AdRequest.Builder().build()
                AppOpenAd.load(
                    controller,
                    Constants.AD_ID_APP_OPEN,
                    requestAds,
                    appOpenAdLoadCallback as AppOpenAdLoadCallback
                )
            }
       */
/* } else {
            onShowAdCompleteListener.appOpenAdsShow()
        }*//*

    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        currentContactActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentContactActivity = activity

        )
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        savedInstanceState: Bundle
    ) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        currentContactActivity = null
    }

    override fun onStart(owner: LifecycleOwner) {
//        if (!Controller.Companion.splashAppOpenAdBoolean) {
//            contactAppOpenAdsRequestSend(currentContactActivity)
//        }
    }

    private fun wasLoadTimeLessThanNHoursAgoAppOpenAdClick(numberHoursLong: Long): Boolean {
        val dateDifference = Date().time - this.loadTimeLong
        val numMilliSecondsPerHour: Long = 3600000
        return (dateDifference < (numMilliSecondsPerHour * numberHoursLong))
    }

    companion object {
        var appOpenShowingBoolean: Boolean = false
        private const val KEY_FAILED_COUNT_APP_OPEN = "KeyFailedCountAppOpen"

        fun resetCounter() {
            resetFailedCountAppOpen()
        }

        private val preference: SharedPreferences
            get() = Controller.getInstance().getSharedPreferences(
                Controller.getInstance().packageName, Context.MODE_PRIVATE
            )

        fun resetFailedCountAppOpen() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_APP_OPEN, 0).apply()
        }

        fun increaseFailedCountAppOpen() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_APP_OPEN, failedCountAppOpen + 1).apply()
        }

        val failedCountAppOpen: Int
            get() {
                val preferences: SharedPreferences = preference
                return preferences.getInt(KEY_FAILED_COUNT_APP_OPEN, 0)
            }


    }
}*/
