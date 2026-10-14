package app.messages.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import app.messages.Controller
import app.messages.Controller.Companion.adsModel
import app.messages.R
import app.messages.analytics.AnalyticsHelper.logAds
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView


/*
class NativeAdLoader {
    private var nativeAdPreload: NativeAd? = null
    private var isFailed: Boolean = false

    //Native Ads
    fun showNativeLarge(
        activity: Activity, ltNativeAds: FrameLayout, ltNativeShimmerAds: FrameLayout
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
            return
        }
        if (adsModel!!.isShowNativeAd.equals("Yes", ignoreCase = true)) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.VISIBLE
            showNative(activity, ltNativeAds, ltNativeShimmerAds, "Large")
        } else {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
        }
    }

    fun showNativeLargeLanguage(
        activity: Activity, ltNativeAds: FrameLayout, ltNativeShimmerAds: FrameLayout,nativeAdCardView: CardView
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
            nativeAdCardView.visibility = View.GONE
            return
        }
        if (adsModel!!.isShowNativeAd.equals("Yes", ignoreCase = true)) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.VISIBLE

            loadAndShowNativeAd(
                activity,
                ltNativeAds,
                ltNativeShimmerAds,
                "Large",
                adsModel!!.nativeAdsIdLanguage!!
            )
        } else {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
            nativeAdCardView.visibility = View.GONE
        }
    }

    fun showNativeSmall(
        activity: Activity, ltNativeAds: FrameLayout, ltNativeShimmerAds: FrameLayout
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
            return
        }
        if (adsModel!!.isShowNativeAd.equals("Yes", ignoreCase = true)) {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.VISIBLE
            showNative(activity, ltNativeAds, ltNativeShimmerAds, "Small")
        } else {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
        }
    }

    private fun showNative(
        activity: Activity,
        ltNativeAds: FrameLayout,
        ltNativeShimmerAds: FrameLayout,
        adType: String
    ) {
        if (adsModel!!.isShowNativeAd.equals("Yes", ignoreCase = true)) {
            if (nativeAdPreload != null) {
                instance!!.inflateGoogleNativeAd(
                    activity,
                    ltNativeAds,
                    ltNativeShimmerAds,
                    nativeAdPreload!!,
                    adType,
                    false
                )
            } else {
                loadAndShowNativeAd(
                    activity,
                    ltNativeAds,
                    ltNativeShimmerAds,
                    adType,
                    adsModel!!.nativeAdsId!!
                )
            }
        } else {
            ltNativeAds.visibility = View.GONE
            ltNativeShimmerAds.visibility = View.GONE
        }
    }

    fun loadNativeAdPreload(activity: Activity) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            return
        }
        if (adsModel!!.nativeAdPreload.equals(
                "Yes", ignoreCase = true
            ) && adsModel!!.isShowNativeAd.equals("Yes", ignoreCase = true)
        ) {
            nativeAdPreload = null
            isFailed = false
            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            val adOptions = NativeAdOptions.Builder().setVideoOptions(videoOptions).build()
            val appLoaderNativeOne =
                AdLoader.Builder(activity, adsModel!!.nativeAdsId!!)
                    .forNativeAd(NativeAd.OnNativeAdLoadedListener { nativeAd: NativeAd? ->
                        logAds(activity, "native_loaded")
                        this.nativeAdPreload = nativeAd
                        isFailed = false
                    }).withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            isFailed = true
                            logAds(activity, "native_failed_to_load_" + adError.code)
                            increaseFailedCountNative()
                        }

                        override fun onAdClicked() {
                            super.onAdClicked()
                            logAds(activity, "native_clicked")
                        }
                    }).withNativeAdOptions(adOptions).build()
            val adRequest = AdRequest.Builder().build()
            logAds(activity, "native_req")
            appLoaderNativeOne.loadAd(adRequest)
        }
    }

    private fun loadAndShowNativeAd(
        activity: Activity,
        ltUniversal: FrameLayout,
        adsNativeBigLoadingBinding: FrameLayout,
        adType: String,
        adUnitID: String
    ) {

        val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
        val adOptions = NativeAdOptions.Builder().setVideoOptions(videoOptions).build()
        val adLoaderNative = AdLoader.Builder(activity, adUnitID)
            .forNativeAd({ nativeAd: NativeAd? ->
                logAds(activity, "native_loaded")
                resetFailedCountNative()
                if (!activity.isDestroyed && !activity.isFinishing) {
                    instance!!.inflateGoogleNativeAd(
                        activity,
                        ltUniversal,
                        adsNativeBigLoadingBinding,
                        nativeAd!!,
                        adType,
                        true
                    )
                } else {
                    nativeAdPreload = nativeAd
                }
            }).withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    ltUniversal.visibility = View.GONE
                    adsNativeBigLoadingBinding.visibility = View.VISIBLE
                    increaseFailedCountNative()
                    logAds(activity, "native_failed_to_load_" + adError.code)
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    logAds(activity, "native_clicked")
                    showNative(activity, ltUniversal, adsNativeBigLoadingBinding, adType)
                }
            }).withNativeAdOptions(adOptions).build()
        val adRequest = AdRequest.Builder().build()
        logAds(activity, "native_req")
        adLoaderNative.loadAd(adRequest)

    }

    private fun inflateGoogleNativeAd(
        activity: Activity,
        ltUniversal: FrameLayout,
        adsNativeBigLoadingBinding: FrameLayout,
        nativeAd: NativeAd,
        adType: String,
        isFromStatic: Boolean?
    ) {
        adsNativeBigLoadingBinding.visibility = View.GONE
        ltUniversal.visibility = View.VISIBLE

        var adView =
            activity.layoutInflater.inflate(
                R.layout.ads_native_big,
                null,
                false
            ) as NativeAdView
        if (adType.equals("Small", ignoreCase = true)) {
            adView = activity.layoutInflater.inflate(
                R.layout.ads_native, null, false
            ) as NativeAdView
        } else if (adType.equals("Large", ignoreCase = true)) {
            adView = activity.layoutInflater.inflate(
                R.layout.ads_native_big, null, false
            ) as NativeAdView
        }

        val install = adView.findViewById<TextView>(R.id.ad_call_to_action)
        adView.mediaView = adView.findViewById<MediaView?>(R.id.ad_media)
        adView.headlineView = adView.findViewById<View?>(R.id.ad_headline)
        adView.bodyView = adView.findViewById<View?>(R.id.ad_body)
        adView.callToActionView = install
        adView.iconView = adView.findViewById<View?>(R.id.ad_app_icon)
        adView.priceView = adView.findViewById<View?>(R.id.ad_price)
        adView.starRatingView = adView.findViewById<View?>(R.id.ad_stars)
        adView.storeView = adView.findViewById<View?>(R.id.ad_store)
        adView.advertiserView = adView.findViewById<View?>(R.id.ad_advertiser)

        install.text = nativeAd.callToAction

        adView.callToActionView = install

        (adView.headlineView as TextView).text = nativeAd.headline

        if (adView.mediaView != null) {
            adView.mediaView!!.mediaContent = nativeAd.mediaContent
        }

        if (nativeAd.body == null) {
            adView.bodyView!!.visibility = View.GONE
        } else {
            adView.bodyView!!.visibility = View.VISIBLE
            if (adView.bodyView != null) {
                (adView.bodyView as TextView).text = nativeAd.body
            }
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView!!.visibility = View.GONE
        } else {
            adView.callToActionView!!.visibility = View.VISIBLE
            if (adView.callToActionView != null) {
                (adView.callToActionView as TextView).text = nativeAd.callToAction
            }
        }

        if (nativeAd.icon == null) {
            adView.iconView!!.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon!!.drawable)
            adView.iconView!!.visibility = View.VISIBLE
        }

        if (nativeAd.price == null) {
            if (adView.priceView != null) {
                adView.priceView!!.visibility = View.GONE
            }
        } else {
            if (adView.priceView != null) {
                adView.priceView!!.visibility = View.VISIBLE
                if (adView.priceView != null) {
                    (adView.priceView as TextView).text = nativeAd.price
                }
            }
        }

        if (nativeAd.store == null) {
            if (adView.storeView != null) {
                adView.storeView!!.visibility = View.GONE
            }
        } else {
            if (adView.storeView != null) {
                adView.storeView!!.visibility = View.VISIBLE
                if (adView.storeView != null) {
                    (adView.storeView as TextView).text = nativeAd.store
                }
            }
        }

        if (nativeAd.starRating == null) {
            if (adView.starRatingView != null) {
                adView.starRatingView!!.visibility = View.GONE
            }
        } else {
            if (adView.starRatingView != null) {
                (adView.starRatingView as RatingBar).rating = nativeAd.starRating!!.toFloat()
                adView.starRatingView!!.visibility = View.VISIBLE
            }
        }

        if (nativeAd.advertiser == null) {
            if (adView.advertiserView != null) {
                adView.advertiserView!!.visibility = View.GONE
            }
        } else {
            if (adView.advertiserView != null) {
                if (adView.advertiserView != null) {
                    (adView.advertiserView as TextView).text = nativeAd.advertiser
                }
                adView.advertiserView!!.visibility = View.VISIBLE
            }
        }

        adView.setNativeAd(nativeAd)

        if (adView.mediaView != null) {
            val vc = nativeAd.mediaContent!!.videoController

            if (nativeAd.mediaContent != null && nativeAd.mediaContent!!.hasVideoContent()) {
                vc.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoEnd() {
                        super.onVideoEnd()
                    }
                }
            }
        }
        ltUniversal.removeAllViews()
        logAds(activity, "native_Showed")
        ltUniversal.addView(adView)

        isFromStatic?.let { isStatic ->
            if (adsModel!!.nativeAdPreload.equals(
                    "Yes", ignoreCase = true
                )
            ) {
                if (!isStatic) {
                    nativeAdPreload = null
                    loadNativeAdPreload(activity)
                } else {
                    if (nativeAdPreload == null && isFailed) {
                        loadNativeAdPreload(activity)
                    }
                }
            } else {
                if (!isStatic) {
                    nativeAdPreload = null
                    loadNativeAdPreload(activity)
                }
            }
        }
    }


    companion object {
        private const val KEY_FAILED_COUNT_NATIVE = "KeyFailedCountNative"

        var instance: NativeAdLoader? = null
            get() {
                if (field == null) {
                    field = NativeAdLoader()
                }
                return field
            }
            private set

        fun resetCounter() {
            resetFailedCountNative()
        }

        private val preference: SharedPreferences
            get() = Controller.getInstance().getSharedPreferences(
                Controller.getInstance().packageName, Context.MODE_PRIVATE
            )

        fun resetFailedCountNative() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_NATIVE, 0).apply()
        }

        private fun increaseFailedCountNative() {
            val preferences: SharedPreferences = preference
            preferences.edit().putInt(KEY_FAILED_COUNT_NATIVE, failedCountNative + 1).apply()
        }


        private val failedCountNative: Int
            get() {
                val preferences: SharedPreferences = preference
                return preferences.getInt(KEY_FAILED_COUNT_NATIVE, 0)
            }

        fun log(log: String?, e: Exception?) {
            }
    }

}*/
