package app.messages.monetization.ads

/**
 * The fields a native ad renders into the "Sponsored" list tile. A real AdMob
 * `NativeAd` maps onto this (task: AdMob wiring); the fake provider returns a
 * sample so the tile is verifiable with ads toggled on in debug.
 */
data class NativeAdContent(
    val headline: String,
    val body: String,
    val advertiser: String,
    val callToAction: String,
)

/** Supplies the current native ad to render, or null when none is loaded. */
interface NativeAdProvider {
    fun current(): NativeAdContent?
}

/** Debug/sample provider until AdMob is wired — returns a clearly-labeled sample. */
class SampleNativeAdProvider @javax.inject.Inject constructor() : NativeAdProvider {
    override fun current(): NativeAdContent = NativeAdContent(
        headline = "Try Notion — your notes, organized",
        body = "The connected workspace for notes, tasks, and docs.",
        advertiser = "Notion Labs",
        callToAction = "Install",
    )
}
