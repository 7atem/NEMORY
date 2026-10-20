package com.vaultbrain.shared.ui.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Returns a UIViewController hosting the shared bilingual sample card.
 *
 * The composition reads [SampleLanguageState] so later language changes recompose in
 * place via [setSampleLanguage] — the view controller itself is never recreated.
 *
 * @param isArabic Initial language; kept for factory compatibility.
 */
fun MainViewController(isArabic: Boolean = false): UIViewController {
    SampleLanguageState.isArabic.value = isArabic
    return ComposeUIViewController {
        val arabic = SampleLanguageState.isArabic.value
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
}
