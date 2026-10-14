package app.messages.monetization.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

@Composable
fun AdaptiveBanner(modifier: Modifier = Modifier, unitId: String = AdUnits.ADAPTIVE_BANNER) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var ready by remember { mutableStateOf(app.messages.config.AdsConfigManager.isInitialized) }
    LaunchedEffect(ready) {
        if (!ready) {
            while (!app.messages.config.AdsConfigManager.isInitialized) delay(250)
            ready = true
        }
    }
    if (!ready) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = unitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {}
                    override fun onAdFailedToLoad(error: LoadAdError) {}
                }
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
