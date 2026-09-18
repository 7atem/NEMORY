package com.vaultbrain.shared.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecoySessionStateTest {

    @Test
    fun testDefaultStateIsNotDecoy() {
        DecoySessionState.exitDecoyMode()
        assertFalse(DecoySessionState.isDecoy)
    }

    @Test
    fun testEnterAndExitDecoyMode() {
        DecoySessionState.enterDecoyMode()
        assertTrue(DecoySessionState.isDecoy)

        DecoySessionState.exitDecoyMode()
        assertFalse(DecoySessionState.isDecoy)
    }

    @Test
    fun testGuardAccessFailsClosedInDecoyMode() {
        DecoySessionState.enterDecoyMode()

        val result = DecoySessionState.guardAccess(onDecoy = "EMPTY") {
            "SECRET_DATA"
        }

        assertEquals("EMPTY", result)

        DecoySessionState.exitDecoyMode()

        val normalResult = DecoySessionState.guardAccess(onDecoy = "EMPTY") {
            "SECRET_DATA"
        }

        assertEquals("SECRET_DATA", normalResult)
    }
}
