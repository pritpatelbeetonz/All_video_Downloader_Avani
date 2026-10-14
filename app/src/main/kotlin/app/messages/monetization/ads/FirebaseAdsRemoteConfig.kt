package app.messages.monetization.ads

import app.messages.di.ApplicationScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FirebaseAdsRemoteConfig @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) : AdsRemoteConfig {
    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    private val _adsEnabled = MutableStateFlow(false) // Default to false when unit IDs are "11"
    override val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()

    // Default static IDs are initialized to "11" as requested to test configuration fetch
    override var appOpenUnitId: String = "11"
        private set
    override var interstitialUnitId: String = "11"
        private set
    override var nativeUnitId: String = "11"
        private set
    override var bannerUnitId: String = "11"
        private set

    init {
        // 1. Define default static JSON structure with values set to "11"
        val defaultJson = """
            {
              "ads_enabled": false,
              "sr_appOpenAd": "11",
              "sr_splashInterstitialAd": "11",
              "sr_preInterstitialAd": "11",
              "sr_introFullNative": "11",
              "sr_surveyBigNative1": "11",
              "sr_bannerAd": "11"
            }
        """.trimIndent()

        val defaults = mapOf(
            app.messages.config.AdsConfigManager.KEY_ADS_CONFIG to defaultJson,
            "messages_chat_sms_text_launcher_app_v2" to defaultJson
        )
        remoteConfig.setDefaultsAsync(defaults)

        // 2. Configure settings (e.g. minimum fetch interval)
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (app.messages.BuildConfig.DEBUG) 0 else 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // 3. Fetch and activate
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateValues()
                }
            }

        // Apply defaults initially
        updateValues()
    }

    private fun updateValues() {
        try {
            var jsonStr = remoteConfig.getString(app.messages.config.AdsConfigManager.KEY_ADS_CONFIG)
            if (jsonStr.isEmpty()) {
                jsonStr = remoteConfig.getString("messages_chat_sms_text_launcher_app_v2")
            }
            if (jsonStr.isEmpty()) return

            val json = JSONObject(jsonStr)

            // Extract master ads enabled flag
            val adsEnabledFromJson = if (json.has("ads_enabled")) {
                json.optBoolean("ads_enabled", false)
            } else {
                false
            }

            val appOpen = json.optString("app_open_ad_id_SplashScreen", json.optString("sr_appOpenAd", ""))
            val splashInterstitial = json.optString("inter_ad_id_SplashScreen", json.optString("sr_splashInterstitialAd", ""))
            val preInterstitial = json.optString("sr_preInterstitialAd", "")
            val surveyNative = json.optString("sr_surveyBigNative1", "")
            val introNative = json.optString("sr_introFullNative", "")
            val bannerAd = json.optString("sr_bannerAd", "")

            // If key units are "11", we treat them as disabled
            val hasValidAds = appOpen != "11" || splashInterstitial != "11" || introNative != "11"

            _adsEnabled.value = adsEnabledFromJson && hasValidAds

            if (appOpen.isNotEmpty() && appOpen != "11") {
                appOpenUnitId = appOpen
            }
            if (splashInterstitial.isNotEmpty() && splashInterstitial != "11") {
                interstitialUnitId = splashInterstitial
            } else if (preInterstitial.isNotEmpty() && preInterstitial != "11") {
                interstitialUnitId = preInterstitial
            }
            if (introNative.isNotEmpty() && introNative != "11") {
                nativeUnitId = introNative
            } else if (surveyNative.isNotEmpty() && surveyNative != "11") {
                nativeUnitId = surveyNative
            }
            if (bannerAd.isNotEmpty() && bannerAd != "11") {
                bannerUnitId = bannerAd
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setEnabledForDebug(value: Boolean) {
        _adsEnabled.value = value
    }
}
