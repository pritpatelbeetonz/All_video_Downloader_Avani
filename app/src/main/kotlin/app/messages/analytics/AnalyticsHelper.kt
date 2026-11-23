package app.messages.analytics

/**
 * Created by Dhara Golakiya on 26-01-2026
 * Email: dharagolakiya4@gmail.com
 */

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import app.messages.analytics.EventScreenName.ADD_PEOPLE
import app.messages.analytics.EventScreenName.AFTER_CALL
import app.messages.analytics.EventScreenName.ARCHIVE_LIST
import app.messages.analytics.EventScreenName.DEFAULT_APP_PERMISSION
import app.messages.analytics.EventScreenName.HOME
import app.messages.analytics.EventScreenName.LANGUAGE
import app.messages.analytics.EventScreenName.ONBOARDING
import app.messages.analytics.EventScreenName.OTHER
import app.messages.analytics.EventScreenName.SELECT_CONTACT
import app.messages.analytics.EventScreenName.SPLASH
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.activity.OnboardingActivity
import app.messages.ui.activity.SplashActivity


object AnalyticsHelper {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    /**
     * Initialize the analytics helper
     * Must be called once in Application.onCreate()
     */
    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }


    fun getActivityName(activity: Activity): String {
        var activityName: String = ""
        if (activity is SplashActivity) {
            activityName = SPLASH
        } else if (activity is LanguageSelectionActivity) {
            activityName = LANGUAGE
        } else if (activity is OnboardingActivity) {
            activityName = ONBOARDING
        } else
            activityName = OTHER

        return activityName
    }

    fun logAdsClick(context: Context, eventName: String?) {
        val params = Bundle()
        params.putString("ActivityName", "ac")
        FirebaseAnalytics.getInstance(context).logEvent("ac_ads_$eventName", params)
        Log.e("ContactAds", "ContactAdsclick_$eventName")
    }

    fun logAds(activity: Activity, eventName: String?) {
        val params = Bundle()
        params.putString("ActivityName", getActivityName(activity))
        FirebaseAnalytics.getInstance(activity)
            .logEvent(getActivityName(activity) + "_ads_" + eventName, params)
        Log.e("ContactAds", "ContactAdsFlow : " + getActivityName(activity) + "_ads_" + eventName)
    }

    fun logActivityView(activity: Activity) {
        val params = Bundle()
        if (activity.callingActivity != null) {
            var className = activity.callingActivity!!.getClassName()
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf(".") + 1)
            }
            params.putString("Parent", className)
        }
        FirebaseAnalytics.getInstance(activity).logEvent(
            getActivityName(
                activity
            ) + "_view", params
        )
    }


    /**
     * Log a custom event
     * @param eventName Name of the event
     * @param params Map of event parameters
     */
    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putFloat(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                // Add more types if needed
            }
        }
        try {
            firebaseAnalytics.logEvent(eventName, bundle)
        } catch (e: Exception) {
            Log.e("AnalyticsHelper", "Error logging event: $eventName", e)
        }
    }

    /**
     * Log a button click event
     */
    fun logEvent(eventName: String, screenName: String) {
        val params = HashMap<String, Any>().apply {
            put("screen", screenName)
        }
        logEvent(eventName, params)
    }

    /**
     * Logs an event strictly ONCE per user lifetime backed by SharedPreferences.
     */
    fun logOnce(context: Context, prefKey: String, eventName: String, params: Bundle = Bundle()) {
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(context)
        if (!prefs.isEventLogged(prefKey)) {
            FirebaseAnalytics.getInstance(context).logEvent(eventName, params)
            prefs.setEventLogged(prefKey, true)
            Log.d("Analytics", "✅ [ONCE_EVENT] Logged event to Firebase: $eventName (params: $params)")
        } else {
            Log.d("Analytics", "⏭️ [ONCE_EVENT] Skipped event $eventName (already logged previously)")
        }
    }
}
