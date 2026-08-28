package com.vaultbrain.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GmailSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun connectAndSync(): List<String> {
        // Simulate OAuth / Google Sign In delay
        delay(2000)
        
        // Return fake emails
        return listOf(
            "BOARDING PASS\nName: AHMED/MOHAMED\nFlight: MS777\nFrom: CAI To: DXB\nDate: 20/12/2026 Time: 10:45\nSeat: 12A Gate: F4",
            "CASHIER: JOHN DOE\nDate: 2026-10-15 14:32\nMilk 1L - 35.00 EGP\nBread - 15.00 EGP\nTOTAL: 50.00 EGP\nTHANK YOU"
        )
    }
}
