package com.vaultbrain.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Google Play Billing gateway for Nemory's one-time remove-ads product. */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ConnectionState { DISCONNECTED, CONNECTING, READY }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionLock = Any()

    @Volatile
    private var billingClient: BillingClient? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { processPurchases(purchases) }
        }
    }

    /** Starts a connection if one is not ready or already being established. */
    fun startConnection() {
        synchronized(connectionLock) {
            if (billingClient?.isReady == true || _connectionState.value == ConnectionState.CONNECTING) {
                return
            }

            _connectionState.value = ConnectionState.CONNECTING
            val client = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
            billingClient = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _connectionState.value = ConnectionState.READY
                    } else {
                        clearClient(client)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    clearClient(client)
                }
            })
        }
    }

    /**
     * Returns owned one-time purchases, or null when Play cannot provide a definitive answer.
     * A definitive empty response is deliberately different from service unavailability.
     */
    suspend fun queryPurchases(): List<Purchase>? {
        val client = awaitReadyClient() ?: return null
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { processPurchases(purchases) }
                    continuation.resume(purchases)
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Opens the purchase sheet for the one-time remove-ads product.
     * An OK result only means that the sheet opened; entitlement comes from purchase updates.
     */
    suspend fun purchaseRemoveAds(activity: Activity): BillingResult {
        val details = refreshProductDetails()
            ?: return billingResult(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
        val client = awaitReadyClient()
            ?: return billingResult(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        return client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    /** Refreshes Play-owned pricing/details immediately before they are displayed or purchased. */
    suspend fun refreshProductDetails(): ProductDetails? {
        val client = awaitReadyClient() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(REMOVE_ADS_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(listOf(product))
                    .build()
            ) { result, queryResult ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                val details = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryResult.productDetailsList.singleOrNull {
                        it.productId == REMOVE_ADS_PRODUCT_ID
                    }
                } else {
                    null
                }
                _productDetails.value = details
                continuation.resume(details)
            }
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>) {
        val relevant = purchases.filter { REMOVE_ADS_PRODUCT_ID in it.products }
        val verified = relevant.filter { purchase ->
            BillingSecurity.verifyPurchase(BASE64_PUBLIC_KEY, purchase)
        }
        verified
            .filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
            }
            .forEach { acknowledge(it) }
        _purchases.value = verified
    }

    private suspend fun acknowledge(purchase: Purchase): Boolean {
        val client = awaitReadyClient() ?: return false
        return suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
            }
        }
    }

    private suspend fun awaitReadyClient(): BillingClient? {
        billingClient?.takeIf { it.isReady }?.let { return it }
        startConnection()
        val state = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            connectionState.first { it != ConnectionState.CONNECTING }
        }
        return if (state == ConnectionState.READY) billingClient?.takeIf { it.isReady } else null
    }

    private fun clearClient(client: BillingClient) {
        synchronized(connectionLock) {
            if (billingClient === client) {
                billingClient = null
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun billingResult(responseCode: Int): BillingResult =
        BillingResult.newBuilder().setResponseCode(responseCode).build()

    companion object {
        /** Must exactly match the one-time product configured in Play Console. */
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
        private const val CONNECTION_TIMEOUT_MS = 10_000L

        /**
         * Base64 public key from Google Play Console (Services & APIs).
         * Set here or supply via BuildConfig to enforce strict local cryptographic validation.
         */
        private val BASE64_PUBLIC_KEY: String? = null
    }
}
