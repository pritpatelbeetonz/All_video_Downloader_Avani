package app.messages.monetization.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.messages.di.ApplicationScope

/**
 * Initializes Google Mobile Ads SDK lazily past first frame.
 */
@Singleton
class AdSdkInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    @Volatile var isReady: Boolean = false
        private set

    fun initializeOnce(onReady: () -> Unit = {}) {
        if (started.getAndSet(true)) {
            if (isReady) onReady()
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                MobileAds.initialize(context) {
                    isReady = true
                    onReady()
                }
            }
        }
    }
}
