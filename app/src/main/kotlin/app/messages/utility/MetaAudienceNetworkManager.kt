package app.messages.utility

import android.content.Context
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.ads.AudienceNetworkAds
import app.messages.preferences.PreferenceHelper

/**
 * Handles dynamic initialization of Facebook SDK and Meta Audience Network SDK
 * using remote configuration parameters fetched from Firebase Remote Config.
 */
object MetaAudienceNetworkManager {
    private const val TAG = "MetaAudienceNetwork"
    private var isInitialized = false
    private var currentAppId: String? = null
    private var currentClientToken: String? = null

    /**
     * Programmatically initializes Facebook SDK and Meta Audience Network SDK with dynamic Remote Config values.
     *
     * @param context Application or Activity context
     * @param appId Facebook App ID (from remote_facebook_app_id)
     * @param clientToken Facebook Client Token (from remote_facebook_client_token)
     * @param onComplete Callback invoked with initialization success status
     */
    fun initialize(
        context: Context,
        appId: String?,
        clientToken: String? = null,
        onComplete: ((success: Boolean) -> Unit)? = null
    ) {
        val trimmedAppId = appId?.trim()
        val trimmedClientToken = clientToken?.trim()

        if (trimmedAppId.isNullOrBlank()) {
            onComplete?.invoke(false)
            return
        }

        if (isInitialized && currentAppId == trimmedAppId && currentClientToken == trimmedClientToken) {
            onComplete?.invoke(true)
            return
        }

        try {
            val appContext = context.applicationContext ?: context

            // 1. Cache the App ID and Client Token in preferences for quick cold boot startup
            PreferenceHelper.getInstance(appContext).setFacebookAppId(trimmedAppId)
            if (!trimmedClientToken.isNullOrBlank()) {
                PreferenceHelper.getInstance(appContext).setFacebookClientToken(trimmedClientToken)
            }

            // 2. Programmatically apply Facebook App ID & Client Token
            FacebookSdk.setApplicationId(trimmedAppId)
            if (!trimmedClientToken.isNullOrBlank()) {
                FacebookSdk.setClientToken(trimmedClientToken)
            }

            // 3. Initialize Facebook SDK
            FacebookSdk.sdkInitialize(appContext) {
                }

            // 4. Initialize Meta Audience Network SDK
            if (!AudienceNetworkAds.isInitialized(appContext)) {
                AudienceNetworkAds.buildInitSettings(appContext)
                    .withInitListener { initResult ->
                        isInitialized = initResult.isSuccess
                        if (initResult.isSuccess) {
                            currentAppId = trimmedAppId
                            currentClientToken = trimmedClientToken
                            } else {
                            }
                        onComplete?.invoke(initResult.isSuccess)
                    }
                    .initialize()
            } else {
                isInitialized = true
                currentAppId = trimmedAppId
                currentClientToken = trimmedClientToken
                onComplete?.invoke(true)
            }
        } catch (e: Exception) {
            onComplete?.invoke(false)
        }
    }

    /**
     * Initializes Meta Audience Network from cached parameters in preferences (called on App onCreate).
     */
    fun initFromCache(context: Context) {
        val cachedAppId = PreferenceHelper.getInstance(context).getFacebookAppId()
        val cachedClientToken = PreferenceHelper.getInstance(context).getFacebookClientToken()
        if (cachedAppId.isNotBlank()) {
            initialize(context, cachedAppId, cachedClientToken)
        }
    }
}
