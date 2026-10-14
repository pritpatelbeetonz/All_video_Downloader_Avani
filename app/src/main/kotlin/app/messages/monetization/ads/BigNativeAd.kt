package app.messages.monetization.ads

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renders a [NativeAd] as a big, brand-styled card.
 */
@Composable
fun BigNativeAd(ad: NativeAd, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val d = ctx.resources.displayMetrics.density
            fun px(v: Int) = (v * d).toInt()
            fun tv(size: Float, color: Int, bold: Boolean, text: String?) = TextView(ctx).apply {
                textSize = size
                setTextColor(color)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                text?.let { this.text = it }
            }

            val badge = tv(10f, Color.WHITE, true, "Ad").apply {
                background = GradientDrawable().apply { setColor(0xFF0057CC.toInt()); cornerRadius = 4f * d }
                setPadding(px(6), px(1), px(6), px(1))
            }
            val sponsored = tv(12f, 0xFF6B7280.toInt(), false, "Sponsored")
            val headline = tv(16f, 0xFF0F1115.toInt(), true, ad.headline)
            val body = tv(13f, 0xFF6B7280.toInt(), false, ad.body).apply { maxLines = 2 }
            val cta = tv(14f, Color.WHITE, true, ad.callToAction ?: "Open").apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(0xFF0057CC.toInt()); cornerRadius = 12f * d }
                setPadding(px(16), px(10), px(16), px(10))
            }
            val media = MediaView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(110)).apply {
                    topMargin = px(8); bottomMargin = px(8)
                }
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge)
                addView(sponsored, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = px(8) })
            }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 16f * d }
                setPadding(px(14), px(14), px(14), px(14))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                addView(topRow)
                addView(headline, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(8) })
                addView(media)
                addView(body)
                addView(cta, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(10) })
            }

            NativeAdView(ctx).apply {
                addView(card)
                headlineView = headline
                bodyView = body
                callToActionView = cta
                mediaView = media
                setNativeAd(ad)
            }
        },
    )
}
