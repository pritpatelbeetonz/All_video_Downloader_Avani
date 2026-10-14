package app.messages.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics
import android.widget.FrameLayout
import app.messages.Controller.Companion.adsModel
import app.messages.R
import app.messages.analytics.AnalyticsHelper.logAds
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import app.messages.constant.Constants

/*class BannerAdLoader {
    private var bannerAdPreload: AdView? = null

    //Banner Ads
    fun loadBannerAdPreload(activity: Activity) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            return
        }
        if (adsModel!!.bannerAdPreload.equals("Yes", ignoreCase = true)) {
            bannerAdPreload = null
            val adView = AdView(activity)
            adView.adUnitId = adsModel!!.bannerAdId!!
            adView.setAdSize(getNormalAdSize(activity))
            val adRequest = AdRequest.Builder().build()
            logAds(activity, "banner_req")
            adView.loadAd(adRequest)
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    logAds(activity, "banner_loaded")
                    bannerAdPreload = adView
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    logAds(activity, "banner_clicked")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    }"
                    )
                    logAds(activity, "banner_failed_to_load_" + loadAdError.code)
                }
            }
        }
    }

    fun showSplashBanner(
        activity: Activity,
        frameLayout: FrameLayout,
        shimmerFrameLayout: FrameLayout
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
            return
        }
//        if ((adsModel?.isShowBannerAdSplash ?: "No").equals("Yes", ignoreCase = true)) {
            loadAndShowBanner(
                activity,
                frameLayout,
                shimmerFrameLayout,
                Constants.AD_ID_SPLASH_BANNER
            )
            frameLayout.visibility = View.VISIBLE
            shimmerFrameLayout.visibility = View.VISIBLE
        *//*} else {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
        }*//*
    }

    fun showBanner(
        activity: Activity,
        frameLayout: FrameLayout,
        shimmerFrameLayout: FrameLayout
    ) {
        if (app.messages.preferences.PreferenceHelper.getInstance(activity).isPremium()) {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
            return
        }
        if (adsModel!!.isShowBannerAd.equals("Yes", ignoreCase = true)) {
            if (bannerAdPreload != null) {
                frameLayout.visibility = View.VISIBLE
                shimmerFrameLayout.visibility = View.GONE

                if (bannerAdPreload!!.parent != null) {
                    (bannerAdPreload!!.parent as ViewGroup).removeView(bannerAdPreload) // <- fix
                }
                frameLayout.removeAllViews()
                logAds(activity, "banner_showed")
                frameLayout.addView(bannerAdPreload)
                bannerAdPreload = null
                loadBannerAdPreload(activity)
            } else {
                loadAndShowBanner(activity,frameLayout,shimmerFrameLayout,adsModel!!.bannerAdId!!)
            }
        } else {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
        }
    }

    private fun loadAndShowBanner(activity: Activity, frameLayout: FrameLayout, shimmerFrameLayout: FrameLayout, adUnitID: String) {
        //Set flAd View Height to Same As Place Holder
        val adView = AdView(activity)
        adView.adUnitId = adUnitID
        //            AdSize adSize = getAdSize(activity);
        adView.setAdSize(getNormalAdSize(activity))
        val adRequest = AdRequest.Builder().build()
        logAds(activity, "banner_req")
        adView.loadAd(adRequest)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                logAds(activity, "banner_loaded")
                frameLayout.visibility = View.VISIBLE
                shimmerFrameLayout.visibility = View.GONE

                if (adView.parent != null) {
                    (adView.parent as ViewGroup).removeView(adView) // <- fix
                }
                frameLayout.removeAllViews()
                logAds(activity, "banner_showed")
                frameLayout.addView(adView)
            }

            override fun onAdClicked() {
                super.onAdClicked()
                logAds(activity, "banner_clicked")
                showBanner(activity, frameLayout, shimmerFrameLayout)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                frameLayout.visibility = View.GONE
                shimmerFrameLayout.visibility = View.GONE
                logAds(activity, "banner_failed_to_load_" + loadAdError.code)
            }
        }
    }

    private fun getNormalAdSize(activity: Activity): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
                windowMetrics.bounds.width()
            } else {
                displayMetrics.widthPixels
            }
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    fun getAdaptiveAdSize(context: Context): AdSize {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val densityDpi = displayMetrics.densityDpi

        val adWidthDp = (displayMetrics.widthPixels / density) - (5 / (densityDpi / 160.0f))

        val adHeightDp =
            (context.resources.getDimension(R.dimen.adaptive_banner_caller_ad_height) / (densityDpi / 160.0f))

        return AdSize.getInlineAdaptiveBannerAdSize(adWidthDp.toInt(), adHeightDp.toInt())
    }

    companion object {
        var instance: BannerAdLoader? = null
            get() {
                if (field == null) {
                    field = BannerAdLoader()
                }
                return field
            }
            private set

    }

}*/
