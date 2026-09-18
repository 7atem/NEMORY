package com.vaultbrain.app.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaultbrain.app.ui.theme.VaultBrainTheme
import com.vaultbrain.shared.ui.sample.SharedSampleScreen

/**
 * Host activity for the Compose Multiplatform bilingual sample.
 *
 * This is a temporary T1 validation surface; it is not part of the production navigation graph.
 */
class SharedSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VaultBrainTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var isArabic by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Shared CMP Sample",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Button(onClick = { isArabic = !isArabic }) {
                            Text(if (isArabic) "Switch to English" else "التبديل إلى العربية")
                        }

                        if (isArabic) {
                            SharedSampleScreen(
                                collectionLabel = "المجموعة",
                                collectionName = "إيصالات البقالة",
                                itemLabel = "العنصر",
                                itemName = "تسوق السبت",
                                amountLabel = "المبلغ",
                                amount = "١٢٣٫٤٥ ر.س",
                                dateLabel = "التاريخ",
                                date = "١٧/٠٩/٢٠٢٦",
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
            }
        }
    }
}
