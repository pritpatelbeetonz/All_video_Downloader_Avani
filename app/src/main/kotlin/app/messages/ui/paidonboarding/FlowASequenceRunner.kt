package app.messages.ui.paidonboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import app.messages.config.AdsConfigManager

/**
 * Runs a Remote Config ordered sequence for one Flow A step (Next on a page, or Language Done), e.g.
 * `flowa_step_sequences: { "page_1": ["inter", "chrome", "app_open", "chrome"] }`.
 *
 * Each token runs in order: `inter` / `app_open` show that ad (continue instantly if none is ready),
 * `chrome` opens the step's web tab and waits for the host to call [onChromeClosed]. After the last
 * token [onDone] runs (move to the next page / Flow A). Steps without a configured sequence are not
 * handled here (see [forStep]) and keep the legacy Chrome -> close-ad behaviour.
 */
class FlowASequenceRunner private constructor(
    private val activity: Activity,
    private val stepTag: String,
    private val tokens: List<Token>,
    /** Opens the web step. Returns false if it could not open (the token is then skipped). */
    private val launchChrome: (onFallback: () -> Unit) -> Boolean,
    private val onDone: () -> Unit,
) {
    enum class Token { INTER, APP_OPEN, CHROME }

    private var index = 0
    private var finished = false

    /** True while the web step is open and we are waiting for it to close. */
    var awaitingChrome = false
        private set

    fun start() {
        Log.d(TAG, "▶️ Sequence for $stepTag: ${tokens.joinToString(" -> ")}")
        advance()
    }

    /** Host calls this from its web-step close callback. */
    fun onChromeClosed() {
        if (!awaitingChrome) return
        awaitingChrome = false
        learning.screenguard.ScreenGuardDemo.onChromeSessionClosed()
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(900L)
        advance()
    }

    private fun advance() {
        if (finished) return
        if (activity.isFinishing || activity.isDestroyed) {
            finished = true
            return
        }
        if (index >= tokens.size) {
            finished = true
            Log.d(TAG, "✅ Sequence for $stepTag finished")
            onDone()
            return
        }
        val token = tokens[index++]
        Log.d(TAG, "   step ${index}/${tokens.size} ($stepTag): $token")
        when (token) {
            Token.INTER -> showAd(FlowAAdManager.FlowAAdFormat.INTERSTITIAL)
            Token.APP_OPEN -> showAd(FlowAAdManager.FlowAAdFormat.APP_OPEN)
            Token.CHROME -> openChrome()
        }
    }

    private fun showAd(format: FlowAAdManager.FlowAAdFormat) {
        // Our own hand-off between steps: don't let the escape guard treat it as the user leaving.
        learning.screenguard.ScreenGuardDemo.noteOwnNavigation(900L)
        FlowAAdManager.showAdOfFormat(activity, format) { advance() }
    }

    private fun openChrome() {
        if (!PaidChromeTabManager.isPaidUser() || !AdsConfigManager.isChromeTabEnabled()) {
            Log.d(TAG, "   chrome skipped ($stepTag): chrome off / not a paid user")
            advance()
            return
        }
        var returned = false
        var failedSync = false
        awaitingChrome = true
        val launched = launchChrome {
            // Called synchronously when the launch fails, or later when the web step is replaced by an
            // App-Open-only ad that has finished.
            if (!returned) failedSync = true else onFallbackLater()
        }
        returned = true
        if (!launched || failedSync) {
            awaitingChrome = false
            Log.d(TAG, "   chrome could not open ($stepTag) -> skipped")
            advance()
        }
    }

    private fun onFallbackLater() {
        if (!awaitingChrome) return
        awaitingChrome = false
        advance()
    }

    companion object {
        private const val TAG = "FlowASequence"

        private fun parse(raw: List<String>): List<Token> = raw.mapNotNull {
            when (it.trim().lowercase()) {
                "inter", "interstitial", "inter_ad" -> Token.INTER
                "app_open", "appopen", "app-open", "open" -> Token.APP_OPEN
                "chrome", "web", "customtab", "tab" -> Token.CHROME
                else -> null
            }
        }

        /** Returns a runner when [stepTag] has a Remote Config sequence (and the user is paid), else null. */
        fun forStep(
            activity: Activity,
            stepTag: String,
            launchChrome: (onFallback: () -> Unit) -> Boolean,
            onDone: () -> Unit,
        ): FlowASequenceRunner? {
            if (!PaidChromeTabManager.isPaidUser()) return null
            val raw = AdsConfigManager.getFlowAStepSequence(stepTag) ?: return null
            return FlowASequenceRunner(activity, stepTag, parse(raw), launchChrome, onDone)
        }

        /** Preloads every ad format the step's sequence will show (called a screen ahead, like today). */
        fun preload(context: Context, stepTag: String) {
            val raw = AdsConfigManager.getFlowAStepSequence(stepTag) ?: return
            parse(raw).toSet().forEach {
                when (it) {
                    Token.INTER -> FlowAAdManager.preloadFormat(context, FlowAAdManager.FlowAAdFormat.INTERSTITIAL)
                    Token.APP_OPEN -> FlowAAdManager.preloadFormat(context, FlowAAdManager.FlowAAdFormat.APP_OPEN)
                    Token.CHROME -> { /* web page is prefetched by PaidChromeTabManager.prefetchStep */ }
                }
            }
        }
    }
}
