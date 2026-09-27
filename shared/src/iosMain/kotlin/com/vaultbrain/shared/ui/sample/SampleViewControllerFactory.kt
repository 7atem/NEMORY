package com.vaultbrain.shared.ui.sample

import platform.UIKit.UIViewController
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class SampleViewControllerFactory {
    var isArabicState by mutableStateOf(false)

    fun create(): UIViewController = ComposeUIViewController {
        if (isArabicState) {
            SharedSampleScreen(
                collectionLabel = "المجموعة",
                collectionName = "إيصالات البقالة",
                itemLabel = "عنصر",
                itemName = "متجر السبت",
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
    
    fun updateLanguage(isArabic: Boolean) {
        isArabicState = isArabic
    }
}
