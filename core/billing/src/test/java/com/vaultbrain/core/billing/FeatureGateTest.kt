package com.vaultbrain.core.billing

import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.Purchase
import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.domain.LensId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FeatureGateTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var billingManager: BillingManager
    private lateinit var purchaseUpdates: MutableStateFlow<List<Purchase>>

    @Before
    fun setUp() {
        context = mockk()
        prefs = mockk()
        editor = mockk(relaxed = true)
        billingManager = mockk()
        purchaseUpdates = MutableStateFlow(emptyList())

        every { context.getSharedPreferences("vaultbrain_feature_gate", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { billingManager.purchases } returns purchaseUpdates
        coEvery { billingManager.queryPurchases() } returns null
    }

    private fun gate(
        entitlement: AdEntitlement? = AdEntitlement.AD_SUPPORTED,
        legacyTier: String? = null
    ): FeatureGate {
        every { prefs.getString("ad_entitlement", null) } returns entitlement?.name
        every { prefs.getString("tier", null) } returns legacyTier
        return FeatureGate(context, billingManager, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun purchase(
        productId: String = BillingManager.REMOVE_ADS_PRODUCT_ID,
        state: Int = Purchase.PurchaseState.PURCHASED
    ): Purchase = mockk {
        every { purchaseState } returns state
        every { products } returns listOf(productId)
    }

    @Test
    fun `all lenses remain available to ad-supported users`() {
        val gate = gate(AdEntitlement.AD_SUPPORTED)

        (LensId.FREE_LENSES + LensId.PRO_LENSES).forEach { lens ->
            assertThat(gate.canAccessLens(lens)).isTrue()
        }
    }

    @Test
    fun `ads are never shown in the ad-free release`() {
        assertThat(gate(AdEntitlement.AD_SUPPORTED).showAds).isFalse()
        assertThat(gate(AdEntitlement.AD_FREE).showAds).isFalse()
    }

    @Test
    fun `legacy paid tier migrates to ad-free entitlement`() {
        val gate = gate(entitlement = null, legacyTier = "PRO")

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_FREE)
        verify { editor.putString("ad_entitlement", "AD_FREE") }
        verify { editor.remove("tier") }
    }

    @Test
    fun `sync grants ad-free only for purchased remove-ads product`() = runTest {
        coEvery { billingManager.queryPurchases() } returns listOf(purchase())
        val gate = gate()

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_FREE)
        verify { editor.putString("ad_entitlement", "AD_FREE") }
        verify { editor.remove("tier") }
    }

    @Test
    fun `different purchased product does not grant ad-free`() = runTest {
        coEvery { billingManager.queryPurchases() } returns listOf(purchase(productId = "different_product"))
        val gate = gate(AdEntitlement.AD_FREE)

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_SUPPORTED)
    }

    @Test
    fun `pending remove-ads purchase does not grant entitlement`() = runTest {
        coEvery { billingManager.queryPurchases() } returns listOf(
            purchase(state = Purchase.PurchaseState.PENDING)
        )
        val gate = gate(AdEntitlement.AD_FREE)

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_SUPPORTED)
    }

    @Test
    fun `definitive empty response revokes cached entitlement`() = runTest {
        coEvery { billingManager.queryPurchases() } returns emptyList()
        val gate = gate(AdEntitlement.AD_FREE)

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_SUPPORTED)
    }

    @Test
    fun `unavailable billing preserves cached entitlement`() = runTest {
        coEvery { billingManager.queryPurchases() } returns null
        val gate = gate(AdEntitlement.AD_FREE)

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_FREE)
    }

    @Test
    fun `billing exception preserves cached entitlement`() = runTest {
        coEvery { billingManager.queryPurchases() } throws IllegalStateException("Play unavailable")
        val gate = gate(AdEntitlement.AD_FREE)

        gate.syncEntitlementWithBilling()

        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_FREE)
    }

    @Test
    fun `realtime purchase updates grant and revoke entitlement`() = runTest {
        val gate = gate()

        purchaseUpdates.value = listOf(purchase())
        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_FREE)

        purchaseUpdates.value = emptyList()
        assertThat(gate.entitlement.value).isEqualTo(AdEntitlement.AD_SUPPORTED)
    }
}
