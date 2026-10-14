package app.messages.utility

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import app.messages.constant.Constants
import app.messages.preferences.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingHelper private constructor(private val context: Context) : PurchasesUpdatedListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var productDetails: ProductDetails? = null
    private var isConnected = false
    private var retryCount = 0
    private val maxRetries = 3

    /**
     * Register a callback here so your UI (PremiumActivity, SettingsFragment, etc.)
     * can refresh immediately after a purchase or restore completes.
     */
    // Callbacks for UI updates
    var onPremiumStatusChanged: ((Boolean) -> Unit)? = null
    var onProductDetailsLoaded: (() -> Unit)? = null
    var onRestoreFinished: ((Boolean) -> Unit)? = null // New: returns true if a purchase was found


    init {
        startConnection()
    }


    // ─────────────────────────────── Connection ───────────────────────────────

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    retryCount = 0
                    queryProductDetails()
                    queryPurchases()   // Always verify subscription on connection
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                if (retryCount < maxRetries) {
                    retryCount++
                    Log.w(TAG, "Billing disconnected – retry #$retryCount")
                    startConnection()
                } else {
                    Log.e(TAG, "Billing disconnected – max retries reached, giving up.")
                }
            }
        })
    }

    // ─────────────────────────── Product details ──────────────────────────────

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Constants.PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            Log.d(TAG, "queryProductDetails response: ${billingResult.responseCode}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list: List<ProductDetails> = queryProductDetailsResult.productDetailsList ?: emptyList()
                Log.d(TAG, "Products found in Play Console: ${list.map { it.productId }}")
                productDetails = list.find { it.productId == Constants.PREMIUM_PRODUCT_ID }
                if (productDetails != null) {

                    Log.d(TAG, "Product details loaded: ${productDetails!!.productId}")
                    // Notify UI to refresh price labels
                    CoroutineScope(Dispatchers.Main).launch {
                        onProductDetailsLoaded?.invoke()
                    }
                } else {

                    Log.e(
                        TAG, "Product '${Constants.PREMIUM_PRODUCT_ID}' not found. " +
                            "Make sure the subscription is active and the tester account " +
                            "is added to the internal testing track in Play Console."
                    )
                }
            } else {
                Log.e(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
            }
        }
    }

    // ─────────────────────────── Query / Restore purchases ────────────────────

    /**
     * Call this to verify or restore purchases (e.g. tapping the Restore button).
     * Automatically reconnects if billing is not yet connected.
     */
    fun queryPurchases() {
        if (!isConnected) {
            startConnection()
            return
        }

        // 1. Query Subscriptions
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
            if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val subsList = subsPurchases ?: emptyList()
                Log.d(TAG, "queryPurchases(SUBS) OK – ${subsList.size} found")
                subsList.forEach { purchase ->
                    Log.d(TAG, "Found SUBS: ${purchase.products} | State: ${purchase.purchaseState}")
                }

                // 2. Query In-App Products (Fallback)
                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                billingClient.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
                    val combinedList = mutableListOf<Purchase>()
                    combinedList.addAll(subsList)

                    if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val inAppList = inAppPurchases ?: emptyList()
                        Log.d(TAG, "queryPurchases(INAPP) OK – ${inAppList.size} found")
                        inAppList.forEach { purchase ->
                            Log.d(TAG, "Found INAPP: ${purchase.products} | State: ${purchase.purchaseState}")
                        }
                        combinedList.addAll(inAppList)
                    }

                    processPurchases(combinedList)
                }
            } else {
                Log.e(TAG, "queryPurchases(SUBS) failed: ${subsResult.debugMessage}")
            }
        }
    }


    // ─────────────────────────── Process purchases ────────────────────────────

    private fun processPurchases(purchases: List<Purchase>) {
        val prefs = PreferenceHelper.getInstance(context)
        var isPremiumActive = false

        for (purchase in purchases) {
            if (!purchase.products.contains(Constants.PREMIUM_PRODUCT_ID)) continue

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    isPremiumActive = true
                    // Must acknowledge within 3 days or Google will refund automatically
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    // Payment is still processing (e.g. cash payment) – do NOT grant premium yet
                    Log.d(TAG, "Purchase PENDING – waiting for payment to complete")
                }
                else -> {
                    Log.w(TAG, "Unknown purchaseState: ${purchase.purchaseState}")
                }
            }
        }

        prefs.setIsPremium(isPremiumActive)
        Log.d(TAG, "Premium status updated to: $isPremiumActive")

        // Notify UI on the main thread so fragments/activities can update
        CoroutineScope(Dispatchers.Main).launch {
            onPremiumStatusChanged?.invoke(isPremiumActive)
            onRestoreFinished?.invoke(isPremiumActive)
        }
    }

    // ──────────────────────── Acknowledge purchase ────────────────────────────

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            val result = billingClient.acknowledgePurchase(params)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully")
            } else {
                Log.e(TAG, "Acknowledge failed [${result.responseCode}]: ${result.debugMessage}")
            }
        }
    }

    // ──────────────────────── PurchasesUpdatedListener ───────────────────────

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases ?: emptyList())
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled the purchase flow")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Subscription is already active – re-query to confirm and update prefs
                Log.d(TAG, "ITEM_ALREADY_OWNED – re-querying purchases")
                queryPurchases()
            }
            else -> {
                Log.e(TAG, "Purchase error [${billingResult.responseCode}]: ${billingResult.debugMessage}")
            }
        }
    }

    // ───────────────────────── Launch billing flow ────────────────────────────

    fun launchBillingFlow(activity: Activity) {
        if (!isConnected) {
            Log.e(TAG, "Billing not connected – reconnecting…")
            startConnection()
            return
        }

        if (productDetails == null) {
            Log.e(TAG, "Product details not loaded yet – reloading…")
            queryProductDetails()
            return
        }

        // For a simple yearly plan there is one offer. If you add free-trial or
        // introductory pricing later, filter subscriptionOfferDetails by offerTag.
        val offerToken = productDetails!!
            .subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (offerToken == null) {
            Log.e(TAG, "Offer token is null – make sure the base plan is active in Play Console")
            return
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails!!)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "launchBillingFlow failed [${result.responseCode}]: ${result.debugMessage}")
        }
    }

    // ─────────────────────────── Price helper ─────────────────────────────────

    /**
     * Returns the formatted price of the base plan phase (e.g. "₹499.00/year").
     * Uses lastOrNull() to skip any free-trial / intro phases and get the recurring price.
     */
    fun getProductPrice(): String? {
        return productDetails
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
    }

    // ──────────────────────────── Singleton ───────────────────────────────────

    companion object {
        private const val TAG = "BillingHelper"

        @Volatile
        private var instance: BillingHelper? = null

        fun getInstance(context: Context): BillingHelper {
            return instance ?: synchronized(this) {
                instance ?: BillingHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

