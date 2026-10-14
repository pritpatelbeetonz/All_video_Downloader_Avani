package app.messages.monetization.ads

/**
 * Ad unit ids + the AdMob app id. These are Google's **public test units** — they
 * serve test ads without an AdMob account, so the integration is verifiable now.
 * Swap each (and [APP_ID]) for the real ids before launch. The Next Gen SDK takes
 * the app id programmatically via InitializationConfig — there is no manifest entry.
 */
object AdUnits {
    /** AdMob **app id** — passed to InitializationConfig.Builder (not the manifest). */
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val NATIVE_ADVANCED = "ca-app-pub-3940256099942544/2247696110"
    const val ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}
