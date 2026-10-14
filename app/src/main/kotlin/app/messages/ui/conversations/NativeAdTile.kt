package app.messages.ui.conversations

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdTile(
    ad: NativeAd,
    onAdViewBound: (NativeAd) -> Unit = {},
    onAdViewReleased: (NativeAd) -> Unit = {},
    modifier: Modifier = Modifier
) {
    android.util.Log.d("ChatAds", "👁️ [ChatAds] AD DISPLAYED ON SCREEN ──> Headline: \"${ad.headline}\"")
    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surface.toArgb()
    val ink = scheme.onSurface.toArgb()
    val muted = scheme.onSurfaceVariant.toArgb()
    val primary = scheme.primary.toArgb()
    val onPrimary = scheme.onPrimary.toArgb()
    val adAmber = Color.parseColor("#C68A00")
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val d = ctx.resources.displayMetrics.density
            fun px(v: Int) = (v * d).toInt()

            val icon = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val drawable = ad.icon?.drawable
                if (drawable != null) setImageDrawable(drawable) else visibility = View.GONE
            }
            val headline = TextView(ctx).apply {
                text = ad.headline
                textSize = 16f
                setTextColor(ink)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val adLabel = TextView(ctx).apply {
                text = "Ad"
                textSize = 11f
                setTextColor(adAmber)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 3f * d
                    setStroke(px(1).coerceAtLeast(1), adAmber)
                }
                setPadding(px(5), px(1), px(5), px(1))
            }
            val body = TextView(ctx).apply {
                text = ad.body
                textSize = 14f
                setTextColor(muted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val cta = TextView(ctx).apply {
                text = (ad.callToAction ?: "Install").uppercase()
                textSize = 12f
                setTextColor(onPrimary)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(primary); cornerRadius = 6f * d }
                setPadding(px(18), px(8), px(18), px(8))
            }

            val titleRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(headline, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(adLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = px(6) })
            }
            val bodyRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(body, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(cta, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = px(12) })
            }
            val textColumn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleRow)
                addView(bodyRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(4) })
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(bgColor)
                setPadding(px(16), px(12), px(16), px(12))
                addView(icon, LinearLayout.LayoutParams(px(52), px(52)).apply { rightMargin = px(14) })
                addView(textColumn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }

            onAdViewBound(ad)
            NativeAdView(ctx).apply {
                addView(row)
                headlineView = headline
                bodyView = body
                callToActionView = cta
                iconView = icon
                setNativeAd(ad)
            }
        },
        onRelease = { nativeAdView ->
            nativeAdView.destroy()
            onAdViewReleased(ad)
        }
    )
}
