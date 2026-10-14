package app.messages.ads.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class AdsModel {

    //ad id
  /*  @SerializedName("app_open_ad_id")
    @Expose
    var appOpenAdId: String? = "ca-app-pub-3940256099942544/9257395921"*/

  /*  @SerializedName("interstitial_ad_id")
    @Expose
    var interAdId: String? = "ca-app-pub-3940256099942544/1033173712"
*/
    /*@SerializedName("banner_ad_id")
    @Expose
    var bannerAdId: String? = "ca-app-pub-3940256099942544/9214589741"*/

  /*  @SerializedName("banner_ad_id_splash")
    @Expose
    var bannerAdIdSplash: String? = "ca-app-pub-3940256099942544/9214589741"*/

  /*  @SerializedName("native_ad_id")
    @Expose
    var nativeAdsId: String? = "ca-app-pub-3940256099942544/1044960115"*/

   /* @SerializedName("native_ad_id_language")
    @Expose
    var nativeAdsIdLanguage: String? = "ca-app-pub-3940256099942544/1044960115"*/

  /*  @SerializedName("native_ad_id_after_call")
    @Expose
    var nativeAdsIdAfterCall: String? = "ca-app-pub-3940256099942544/1044960115"*/


    //show or not
    @SerializedName("show_banner_ad")
    @Expose
    var isShowBannerAd: String? = "Yes"

   /* @SerializedName("show_banner_ad_splash")
    @Expose
    var isShowBannerAdSplash: String? = "Yes"

    @SerializedName("show_interstitial_ad")
    @Expose
    var isShowInterstitialAd: String? = "Yes"*/

    @SerializedName("show_native_ad")
    @Expose
    var isShowNativeAd: String? = "Yes"

   /* @SerializedName("show_app_open_ad")
    @Expose
    var isShowAppOpenAd: String? = "Yes"


    // inter count
    @SerializedName("interstitial_forward_ad_count")
    @Expose
    var interstitialForwardAdCount: Int = 20



    // preload
    @SerializedName("app_open_ad_preload")
    @Expose
    var appOpenAdPreload: String? = "No"

    @SerializedName("native_ad_preload")
    @Expose
    var nativeAdPreload: String? = "Yes"

    @SerializedName("banner_ad_preload")
    @Expose
    var bannerAdPreload: String? = "No"
*/

    //language
    @SerializedName("display_language_screen")
    @Expose
    var displayLanguageScreen: String? = "Yes"

}