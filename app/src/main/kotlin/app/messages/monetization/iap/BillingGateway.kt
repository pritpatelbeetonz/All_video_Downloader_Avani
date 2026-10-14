package app.messages.monetization.iap

import javax.inject.Inject
import javax.inject.Singleton

/** A purchasable "Messages Plus" SKU (build doc §6.3). */
data class PlusProduct(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val isBestValue: Boolean = false,
    val trialEligible: Boolean = false,
)

object PlusSkus {
    const val WEEKLY = "plus_weekly"
    const val YEARLY = "plus_yearly"
    const val LIFETIME = "plus_lifetime"
}

/**
 * Purchase + restore surface (build doc §6.3). The local impl grants the
 * entitlement directly — it makes the paywall fully exercisable in debug
 * (purchase → premium → ads vanish) without Play Billing. The RevenueCat impl
 * (Offerings + `purchase()` + `restorePurchases()`, writing through to
 * [EntitlementController]) replaces this binding once an API key is set.
 */
interface BillingGateway {
    val products: List<PlusProduct>
    suspend fun purchase(productId: String, trial: Boolean): Boolean
    suspend fun restore(): Boolean
}

@Singleton
class LocalBillingGateway @Inject constructor(
    private val entitlement: EntitlementController,
) : BillingGateway {

    // INR pricing tier from §6.3; real prices come from RevenueCat Offerings.
    override val products: List<PlusProduct> = listOf(
        PlusProduct(PlusSkus.YEARLY, "Yearly", "₹699", "per year", isBestValue = true, trialEligible = true),
        PlusProduct(PlusSkus.WEEKLY, "Weekly", "₹99", "per week", trialEligible = true),
        PlusProduct(PlusSkus.LIFETIME, "Lifetime", "₹1,499", "one-time"),
    )

    override suspend fun purchase(productId: String, trial: Boolean): Boolean {
        // Debug: grant immediately. Real billing verifies + acknowledges first.
        entitlement.setPremium(true)
        return true
    }

    override suspend fun restore(): Boolean = entitlement.isPremium.value
}
