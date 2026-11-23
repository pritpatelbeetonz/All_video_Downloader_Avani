package app.messages.attribution

/**
 * Unity Ads install: Unity's store link leaves its own values in the Play referrer, usually
 * containing "unity". Matched against unityUtm.
 */
internal object UnitySourceDetector {

    /** The matched marker, or null when this isn't a Unity install. */
    fun detect(referrer: PlayReferrer, config: AttributionConfig): String? {
        AttributionLog.d("🟢 [Unity] Checking...")
        AttributionLog.d("🟢 [Unity] Referrer markers from Remote Config (unityUtm): ${config.unityUtm}")
        if (!referrer.isOk) {
            AttributionLog.d("🟢 [Unity] ⏭️ Skipped: no Play referrer")
            return null
        }
        val marker = containsMarker(referrer.raw.orEmpty(), config.unityUtm)
        if (marker != null) {
            AttributionLog.d("🟢 [Unity] ✅ Play referrer contains \"$marker\"")
        } else {
            AttributionLog.d("🟢 [Unity] ❌ No match for unityUtm: ${config.unityUtm}")
        }
        return marker
    }
}
