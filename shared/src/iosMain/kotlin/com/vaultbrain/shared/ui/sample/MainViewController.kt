package com.vaultbrain.shared.ui.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Returns a UIViewController hosting the shared bilingual sample card.
 *
 * @param isArabic When true, renders the sample in Arabic right-to-left layout.
 */
fun MainViewController(isArabic: Boolean = false): UIViewController = ComposeUIViewController {
    if (isArabic) {
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
