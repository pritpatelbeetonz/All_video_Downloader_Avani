package app.messages.attribution

import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Ads install: decided from the Play referrer only.
 *
 * Google Ads adds `gclid` (a click id) to every ad click by itself (auto-tagging), so a referrer
 * with a gclid came from a Google ad. Two stages, first match wins:
 *   1. isCheckGoogleUtmList && the raw referrer contains a marker from googleAdUTMSourceCheckStringList
 *   2. any rule in googleReferrerRules matches the referrer parameters
 *
 * Organic Play installs ("utm_source=google-play&utm_medium=organic") match neither.
 */
internal object GoogleSourceDetector {

    /** What matched ("marker:<text>" or "rule:<index>"), or null when this isn't a Google install. */
    fun detect(referrer: PlayReferrer, config: AttributionConfig): String? {
        AttributionLog.d("🔵 [Google] Checking...")
        if (!referrer.isOk) {
            AttributionLog.d("🔵 [Google] ⏭️ Skipped: no Play referrer")
            return null
        }
        AttributionLog.d("🔵 [Google] Params: ${referrer.params}")

        // Stage 1: markers in the raw text
        if (config.isCheckGoogleUtmList) {
            containsMarker(referrer.raw.orEmpty(), config.googleMarkers)?.let {
                AttributionLog.d("🔵 [Google] ✅ Matched marker \"$it\"")
                return "marker:$it"
            }
        }

        // Stage 2: rules on the parsed parameters
        config.googleReferrerRules.forEachIndexed { i, rule ->
            if (matchesRule(referrer.params, rule)) {
                AttributionLog.d("🔵 [Google] ✅ Matched rule $i: $rule")
                return "rule:$i"
            }
        }

        AttributionLog.d("🔵 [Google] ❌ No match")
        return null
    }

    /**
     * Every key in the rule must match:
     *   "*"            -> the parameter is present and not empty
     *   "google"       -> equals (case-insensitive)
     *   ["cpc", "ppc"] -> equals any item (case-insensitive)
     */
    fun matchesRule(params: Map<String, String>, rule: JSONObject): Boolean {
        if (rule.length() == 0) return false
        for (key in rule.keys()) {
            val value = params[key.lowercase()]
            if (value.isNullOrEmpty()) return false
            when (val expected = rule.opt(key)) {
                "*" -> continue
                is JSONArray -> if ((0 until expected.length()).none { expected.optString(it).equals(value, ignoreCase = true) }) return false
                is String -> if (!expected.equals(value, ignoreCase = true)) return false
                else -> return false
            }
        }
        return true
    }
}
