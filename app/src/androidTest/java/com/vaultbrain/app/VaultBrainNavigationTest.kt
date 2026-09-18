package com.vaultbrain.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the three current tabs without modifying vault records. */
@RunWith(AndroidJUnit4::class)
class VaultBrainNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tabsRemainReachable() {
        val labels = listOf(R.string.nav_today, R.string.nav_vault, R.string.nav_brain)
            .map { compose.activity.getString(it) }
        labels.forEach { label ->
            compose.onAllNodesWithText(label).onFirst().assertIsDisplayed().performClick()
            compose.waitForIdle()
        }
        compose.onAllNodesWithText(labels.first()).onFirst().performClick()
    }
}
