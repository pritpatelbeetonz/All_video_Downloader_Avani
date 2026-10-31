package app.messages.attribution

import org.json.JSONArray
import org.json.JSONObject

/**
 * Install-source settings, read from the ads JSON in Remote Config (AdsConfigManager.KEY_ADS_CONFIG,
 * the same value the splash fetches); these fields sit next to the ad settings.
 * (one JSON value). Every key is optional: anything missing, or the whole value being empty or
 * invalid, falls back to the defaults below, so detection works before the key is ever created.
 *
 * Current value (Google only; Facebook and Unity stay off by default):
 * {
 *   "isShowRegularToAll": false,
 *   "isSimCheckAllow": false,
 *   "blockedCountriesForFacebookContent": [],
 *   "isCheckGoogleSource": true,
 *   "isCheckGoogleUtmList": true,
 *   "googleAdUTMSourceCheckStringList": [],
 *   "googleReferrerRules": [
 *     { "gclid": "*" },
 *     { "utm_source": ["google"], "utm_medium": ["cpc", "ppc", "paid"] }
 *   ],
 *   "isFirestoreEnabled": true,
 *   "firestoreReadTimeoutInSeconds": 5,
 *   "referrerCheckTimeoutInSeconds": 30,
 *   "playReferrerTimeoutInSeconds": 10
 * }
 *
 * To turn Facebook / Unity on later, add:
 *   "isCheckFacebookSource": true, "fbUtm": [...], "fbUtmsForDeferrerLink": ["facebook", "fbclid"],
 *   "isCheckUnitySource": true, "unityUtm": ["unity"]
 */
internal data class AttributionConfig(
    /**
     * Emergency switch, as in the reference app: on = every user is "regular" and no checks run.
     * Default ON (detection off) until Remote Config sets it to false.
     */
    val isShowRegularToAll: Boolean = true,

    // ---- Device checks (DeviceChecks), before every source check, as in the reference ----
    /** On: a phone without a SIM card is regular (release builds only). Default off. */
    val isSimCheckAllow: Boolean = false,
    /**
     * Upper-case country codes whose users are regular, whatever the referrer says (the name is the
     * reference app's; it applies to every source). ["NP","PK"] and ["NP,PK"] are read alike. Default none.
     */
    val blockedCountries: List<String> = emptyList(),

    // ---- Google (GoogleSourceDetector) ----
    val isCheckGoogleSource: Boolean = true,
    /** Stage 1: Google when the raw referrer contains any of [googleMarkers]. */
    val isCheckGoogleUtmList: Boolean = true,
    val googleMarkers: List<String> = emptyList(),
    /** Stage 2: Google when any rule matches (see GoogleSourceDetector.matchesRule). */
    val googleReferrerRules: List<JSONObject> = defaultGoogleRules(),

    // ---- Facebook (FacebookSourceDetector) ---- off until Remote Config turns it on
    val isCheckFacebookSource: Boolean = false,
    /** Facebook when the Play referrer contains any of these. */
    val fbUtm: List<String> = emptyList(),
    /** Facebook when the Facebook deferred deep link contains any of these. */
    val fbUtmsForDeferrerLink: List<String> = listOf("facebook", "fbclid"),
    /** Tries at fetching the deferred link, [fbAwaitSecondsBetweenTries] apart (the SDK may still be starting). */
    val fbDeepLinkTries: Int = 2,
    val fbAwaitSecondsBetweenTries: Int = 2,
    val fbDeferredLinkTimeoutSec: Int = 5,

    // ---- Unity (UnitySourceDetector) ---- off until Remote Config turns it on
    val isCheckUnitySource: Boolean = false,
    val unityUtm: List<String> = listOf("unity"),

    // ---- Firestore (FirestoreSourceStore) ----
    /** Reinstalls keep their earlier ad source via Firestore install_referrers/<device key>. */
    val isFirestoreEnabled: Boolean = true,
    val firestoreReadTimeoutSec: Int = 5,

    // ---- Timeouts ----
    /** Whole detection; past this the user is saved as organic. */
    val referrerCheckTimeoutSec: Int = 30,
    val playReferrerTimeoutSec: Int = 10,
) {
    companion object {

        fun fromJson(raw: String?): AttributionConfig {
            val d = AttributionConfig()
            if (raw.isNullOrBlank()) return d
            val json = runCatching { JSONObject(raw) }.getOrElse {
                AttributionLog.w("⚠️ Ads config is not valid JSON; install source defaults used")
                return d
            }
            return AttributionConfig(
                isShowRegularToAll = json.optBoolean("isShowRegularToAll", d.isShowRegularToAll),
                isSimCheckAllow = json.optBoolean("isSimCheckAllow", d.isSimCheckAllow),
                blockedCountries = json.stringList("blockedCountriesForFacebookContent")
                    ?.flatMap { it.split(",") }
                    ?.map { it.trim().uppercase() }
                    ?.filter { it.isNotEmpty() }
                    ?: d.blockedCountries,
                isCheckGoogleSource = json.optBoolean("isCheckGoogleSource", d.isCheckGoogleSource),
                isCheckGoogleUtmList = json.optBoolean("isCheckGoogleUtmList", d.isCheckGoogleUtmList),
                googleMarkers = json.stringList("googleAdUTMSourceCheckStringList") ?: d.googleMarkers,
                googleReferrerRules = json.optJSONArray("googleReferrerRules")
                    ?.let { rules -> (0 until rules.length()).mapNotNull { rules.optJSONObject(it) } }
                    ?: d.googleReferrerRules,
                isCheckFacebookSource = json.optBoolean("isCheckFacebookSource", d.isCheckFacebookSource),
                fbUtm = json.stringList("fbUtm") ?: d.fbUtm,
                fbUtmsForDeferrerLink = json.stringList("fbUtmsForDeferrerLink") ?: d.fbUtmsForDeferrerLink,
                fbDeepLinkTries = json.optInt("loopCountForFbDeepLinkCheck", d.fbDeepLinkTries),
                fbAwaitSecondsBetweenTries =
                    json.optInt("awaitSecondCountForFbDeepLinkCheck", d.fbAwaitSecondsBetweenTries),
                fbDeferredLinkTimeoutSec = json.optInt("fbDeferredLinkTimeoutInSeconds", d.fbDeferredLinkTimeoutSec),
                isCheckUnitySource = json.optBoolean("isCheckUnitySource", d.isCheckUnitySource),
                unityUtm = json.stringList("unityUtm") ?: d.unityUtm,
                isFirestoreEnabled = json.optBoolean("isFirestoreEnabled", d.isFirestoreEnabled),
                firestoreReadTimeoutSec = json.optInt("firestoreReadTimeoutInSeconds", d.firestoreReadTimeoutSec),
                referrerCheckTimeoutSec = json.optInt("referrerCheckTimeoutInSeconds", d.referrerCheckTimeoutSec),
                playReferrerTimeoutSec = json.optInt("playReferrerTimeoutInSeconds", d.playReferrerTimeoutSec),
            )
        }

        private fun JSONObject.stringList(key: String): List<String>? {
            val array: JSONArray = optJSONArray(key) ?: return null
            return (0 until array.length()).map { array.optString(it) }
        }

        // gclid present = a Google Ads click; or source google with a paid medium.
        private fun defaultGoogleRules() = listOf(
            JSONObject().put("gclid", "*"),
            JSONObject()
                .put("utm_source", JSONArray(listOf("google")))
                .put("utm_medium", JSONArray(listOf("cpc", "ppc", "paid"))),
        )
    }
}

/** Case-insensitive; empty markers are skipped because "".contains would match every text. */
internal fun containsMarker(text: String, markers: List<String>): String? {
    val lower = text.lowercase()
    return markers.firstOrNull { it.isNotEmpty() && lower.contains(it.lowercase()) }
}
