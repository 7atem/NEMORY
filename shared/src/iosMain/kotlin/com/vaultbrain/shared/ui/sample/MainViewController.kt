package com.vaultbrain.shared.ui.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Returns a UIViewController hosting the shared bilingual sample card.
 *
 * The language toggle lives entirely inside the Compose tree: it mutates
 * [SampleLanguageState], which recomposes the hosted content in place. The
 * UIViewController is created exactly once and never updated from SwiftUI —
 * recreating or cross-boundary state writes deadlock Kotlin/Native under
 * XCUITest.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val arabic = SampleLanguageState.isArabic.value

    SampleLanguageToggle(arabic)

    if (arabic) {
        SharedSampleScreen(
            collectionLabel = "المجموعة",
            collectionName = "إيصالات البقالة",
            itemLabel = "عنصر",
            itemName = "تسوق السبت",
            amountLabel = "المبلغ",
            amount = "123.45 دولار",
            dateLabel = "التاريخ",
            date = "17 سبتمبر 2026",
            isRtl = true
        )
    } else {
        SharedSampleScreen(
            collectionLabel = "Collection",
            collectionName = "Grocery receipts",
            itemLabel = "Item",
            itemName = "Saturday shop",
            amountLabel = "Amount",
            amount = "$123.45",
            dateLabel = "Date",
            date = "17 Sep 2026",
            isRtl = false
        )
    }
}

@Composable
private fun SampleLanguageToggle(isArabic: Boolean) {
    Button(
        onClick = { setSampleLanguage(!isArabic) },
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("languageToggle")
    ) {
        Text(
            text = if (isArabic) "Switch to English" else "التبديل إلى العربية",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
