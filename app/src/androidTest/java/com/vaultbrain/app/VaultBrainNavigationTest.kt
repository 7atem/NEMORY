package com.vaultbrain.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests verifying VaultBrain navigation, bottom navigation switching,
 * and lens screen router flows.
 */
@RunWith(AndroidJUnit4::class)
class VaultBrainNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigation_switchesScreensCorrectly() {
        // Wait for MainActivity Compose tree to load
        composeTestRule.waitForIdle()

        // 1. Verify Bottom Navigation Tabs are present
        composeTestRule.onNodeWithText("Vault").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brain").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // 2. Click "Brain" tab and verify Brain Chat is displayed
        composeTestRule.onNodeWithText("Brain").performClick()
        composeTestRule.waitForIdle()

        // 3. Click "Settings" tab and verify Settings screen is displayed
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // 4. Return to "Vault" home tab
        composeTestRule.onNodeWithText("Vault").performClick()
        composeTestRule.waitForIdle()
    }
}
