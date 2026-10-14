package app.messages.ui.onboarding

import androidx.lifecycle.ViewModel
import app.messages.monetization.ads.NativeAdManager
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SurveyAdViewModel @Inject constructor(
    nativeAdManager: NativeAdManager,
) : ViewModel() {
    val ad: StateFlow<NativeAd?> = nativeAdManager.ad

    init {
        nativeAdManager.ensureLoaded()
    }
}
