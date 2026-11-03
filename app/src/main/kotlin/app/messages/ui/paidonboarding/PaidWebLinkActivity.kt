package app.messages.ui.paidonboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * In-app WebView fallback for the paid onboarding web step — used when NO Custom-Tabs-capable
 * browser is installed (the same role as the reference app's WebLinkActivity).
 *
 * Why a WebView here: because the page lives in OUR OWN activity, the screen guard treats it like
 * any onboarding screen (it's listed in ScreenGuardDemo.ONBOARDING_SCREENS). Leaving it therefore
 * triggers the permission-free pull-back (started-activity counter + moveTaskToFront) — the full
 * reordering/trap behaviour, with NO Usage Access and NO Custom Tab needed.
 *
 * Safe-by-default (WebViews are risky — see the cert/scheme notes):
 *  - JavaScript ON (landing pages need it) but NO addJavascriptInterface (no JS→native bridge).
 *  - SSL errors are CANCELLED, never proceed()'d — security + Google Play policy. Fail closed.
 *  - Non-http(s) links (market://, intent://, tel:, mailto:, …) are routed to the system, not the WebView.
 */
class PaidWebLinkActivity : AppCompatActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            Log.w(TAG, "No URL extra → finish")
            finishOk()
            return
        }

        val wv = WebView(this)
        webView = wv
        setContentView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Deliberately NOT adding a JavascriptInterface — that is the classic JS→native injection hole.
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url ?: return false
                return when (u.scheme?.lowercase()) {
                    "http", "https" -> false   // load inside the WebView
                    else -> {
                        // market://, intent://, tel:, mailto:, … → hand off to the system (what Chrome does for us).
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            Log.w(TAG, "No app for scheme '${u.scheme}': ${e.message}")
                        }
                        true
                    }
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // NEVER proceed() on a bad/expired/self-signed cert — security hole + Play rejection. Fail closed.
                Log.w(TAG, "SSL error ${error.primaryError} for ${error.url} → cancel (not proceeding)")
                handler.cancel()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val back = webView
                if (back != null && back.canGoBack()) back.goBack() else finishOk()
            }
        })

        Log.d(TAG, "🌐 [WebView] loading $url")
        wv.loadUrl(url)
    }

    private fun finishOk() {
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaidWebLink"
        const val EXTRA_URL = "web_url"

        fun intent(context: Context, url: String): Intent =
            Intent(context, PaidWebLinkActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
