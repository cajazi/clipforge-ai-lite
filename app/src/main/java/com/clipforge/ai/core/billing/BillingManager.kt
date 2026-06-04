package com.clipforge.ai.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BillingManager"

data class BillingState(
    val isConnected: Boolean = false,
    val isPro: Boolean = false,
    val products: List<ProductDetails> = emptyList(),
    val errorMessage: String? = null
)

class BillingManager(
    private val entitlementManager: EntitlementManager
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _billingState = MutableStateFlow(BillingState())
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    fun initialize(context: Context) {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    _billingState.value = _billingState.value.copy(isConnected = true)
                    scope.launch {
                        queryProducts()
                        queryExistingPurchases()
                    }
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    _billingState.value = _billingState.value.copy(errorMessage = result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected — retrying")
                _billingState.value = _billingState.value.copy(isConnected = false)
                connectToGooglePlay()
            }
        })
    }

    private suspend fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SubscriptionPlan.PRO_MONTHLY_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SubscriptionPlan.PRO_YEARLY_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val products = result.productDetailsList ?: emptyList()
            _billingState.value = _billingState.value.copy(products = products)
            Log.d(TAG, "Found ${products.size} products")
        }
    }

    private suspend fun queryExistingPurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchases(result.purchasesList)
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val product = _billingState.value.products.find { it.productId == productId }
        if (product == null) {
            Log.e(TAG, "Product $productId not found")
            return
        }

        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Launch billing flow failed: ${result.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { scope.launch { processPurchases(it) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${result.debugMessage}")
                _billingState.value = _billingState.value.copy(errorMessage = result.debugMessage)
            }
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>) {
        var hasPro = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase.purchaseToken)
                }
                if (purchase.products.any { it == SubscriptionPlan.PRO_MONTHLY_ID || it == SubscriptionPlan.PRO_YEARLY_ID }) {
                    hasPro = true
                }
            }
        }
        entitlementManager.setPlan(if (hasPro) PlanType.PRO else PlanType.FREE)
        _billingState.value = _billingState.value.copy(isPro = hasPro)
        Log.d(TAG, "isPro = $hasPro")
    }

    private suspend fun acknowledgePurchase(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Purchase acknowledged")
        }
    }

    fun querySubscriptions() {
        scope.launch { queryExistingPurchases() }
    }

    fun isReady(): Boolean = billingClient.isReady
}
