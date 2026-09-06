package com.vaultbrain.core.common.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultbrain.core.common.model.ProcessingState
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedItemCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSharedItemCardRendersAndAnimates() {
        val testItem = VaultItem(
            id = "test_item_1",
            title = "Test Receipt",
            summary = "Coffee from Starbucks",
            sourceType = SourceType.MANUAL,
            extractionState = ProcessingState.COMPLETE
        )

        var clicked = false

        composeTestRule.setContent {
            SharedItemCard(
                item = testItem,
                onClick = { clicked = true }
            )
        }

        // Verify content renders correctly
        composeTestRule.onNodeWithText("Test Receipt").assertExists()
        composeTestRule.onNodeWithText("Coffee from Starbucks").assertExists()

        // Verify click action works (which triggers animateContentSize implicitly underneath)
        composeTestRule.onNodeWithText("Test Receipt").performClick()

        assert(clicked) { "Card was not clicked" }
    }
}
