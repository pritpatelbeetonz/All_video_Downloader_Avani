package app.messages.monetization.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UMP (User Messaging Platform) consent — the GDPR/EEA consent flow that must
 * complete before any ad request (build doc §6.2). Outside the EEA the form
 * isn't required and [canRequestAds] is immediately true. Runs on a real
 * Activity because the consent form is a full-screen dialog.
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    /** Requests consent info and shows the form if required; [onReady] fires
     *  once ads may be requested. */
    fun ensureConsent(activity: Activity, onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInformation.canRequestAds()) onReady()
                }
            },
            { /* update failed — leave ads un-requested this session */ },
        )
        // If consent was already resolved in a prior session, proceed now.
        if (consentInformation.canRequestAds()) onReady()
    }
}
