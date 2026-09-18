package com.vaultbrain.core.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Single source of truth for ad entitlement.
 *
 * Every app and AI feature is available regardless of this state. The cached value is retained
 * only when Play is unavailable; a definitive Play response always grants or revokes it.
 */
@Singleton
class FeatureGate internal constructor(
    context: Context,
    private val billingManager: BillingManager,
    private val scope: CoroutineScope
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        billingManager: BillingManager
    ) : this(
        context = context,
        billingManager = billingManager,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _entitlement = MutableStateFlow(readInitialEntitlement())
    val entitlement: StateFlow<AdEntitlement> = _entitlement.asStateFlow()

    val showAds: Boolean get() = false

    init {
        scope.launch { syncEntitlementWithBilling() }
        scope.launch {
            billingManager.purchases.drop(1).collect(::applyDefinitivePurchases)
        }
    }

    /** Refreshes the cached entitlement. Failure preserves the last definitive Play result. */
    suspend fun syncEntitlementWithBilling() {
        val purchases = runCatching { billingManager.queryPurchases() }.getOrNull() ?: return
        applyDefinitivePurchases(purchases)
    }

    /** Feature access is universal; monetization only controls ad visibility. */
    @Suppress("UNUSED_PARAMETER")
    fun canAccessLens(lensId: String): Boolean = true

    private fun applyDefinitivePurchases(purchases: List<Purchase>) {
        setEntitlement(
            if (purchases.any(::isOwnedRemoveAdsPurchase)) {
                AdEntitlement.AD_FREE
            } else {
                AdEntitlement.AD_SUPPORTED
            }
        )
    }

    private fun isOwnedRemoveAdsPurchase(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            BillingManager.REMOVE_ADS_PRODUCT_ID in purchase.products

    private fun setEntitlement(value: AdEntitlement) {
        _entitlement.value = value
        prefs.edit {
            putString(KEY_AD_ENTITLEMENT, value.name)
            remove(LEGACY_KEY_TIER)
        }
    }

    private fun readInitialEntitlement(): AdEntitlement {
        prefs.getString(KEY_AD_ENTITLEMENT, null)?.let { saved ->
            return runCatching { enumValueOf<AdEntitlement>(saved) }
                .getOrDefault(AdEntitlement.AD_SUPPORTED)
        }

        // Existing paid builds stored PRO/ULTRA. Preserve that ad-free state once, then migrate.
        val migrated = when (prefs.getString(LEGACY_KEY_TIER, null)) {
            "PRO", "ULTRA" -> AdEntitlement.AD_FREE
            else -> AdEntitlement.AD_SUPPORTED
        }
        prefs.edit {
            putString(KEY_AD_ENTITLEMENT, migrated.name)
            remove(LEGACY_KEY_TIER)
        }
        return migrated
    }

    companion object {
        private const val PREFS_NAME = "vaultbrain_feature_gate"
        private const val KEY_AD_ENTITLEMENT = "ad_entitlement"
        private const val LEGACY_KEY_TIER = "tier"
    }
}
