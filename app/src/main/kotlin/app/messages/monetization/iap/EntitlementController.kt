package app.messages.monetization.iap

import app.messages.core.prefs.AppPrefs
import app.messages.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Source of premium ("Messages Plus") state. The local impl is backed by
 * DataStore; the RevenueCat impl (added when an API key is configured) observes
 * `CustomerInfo` entitlements and writes through to the same local cache so the
 * UI stays correct offline. Build doc §6.3.
 */
interface EntitlementSource {
    val isPremium: Flow<Boolean>
    suspend fun refresh()
    suspend fun grantLocal(value: Boolean)
}

/** DataStore-backed entitlement — also the write-through cache for RevenueCat. */
@Singleton
class LocalEntitlementSource @Inject constructor(
    private val prefs: AppPrefs,
) : EntitlementSource {
    override val isPremium: Flow<Boolean> = prefs.premiumLocal
    override suspend fun refresh() { /* nothing to revalidate locally */ }
    override suspend fun grantLocal(value: Boolean) = prefs.setPremiumLocal(value)
}

/**
 * The single thing the rest of the app asks: "is the user premium?" Premium
 * removes every ad and unlocks all gated features.
 */
@Singleton
class EntitlementController @Inject constructor(
    private val source: EntitlementSource,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val isPremium: StateFlow<Boolean> =
        source.isPremium.stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun refresh() = source.refresh()

    /** Used by the debug premium toggle and by a successful purchase result. */
    suspend fun setPremium(value: Boolean) = source.grantLocal(value)
}
