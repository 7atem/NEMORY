package com.vaultbrain.shared.ui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A small bilingual collection/item-card sample used to evaluate Compose Multiplatform.
 *
 * The caller supplies localized labels and text direction so the same composable can render
 * English (LTR) and Arabic (RTL) without depending on platform resource systems.
 */
@Composable
fun SharedSampleScreen(
    collectionLabel: String,
    collectionName: String,
    itemLabel: String,
    itemName: String,
    amountLabel: String,
    amount: String,
    dateLabel: String,
    date: String,
    isRtl: Boolean,
    modifier: Modifier = Modifier
) {
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isRtl) "بطاقة Nemory التجريبية" else "Nemory Sample Card",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledLine(label = collectionLabel, value = collectionName)
                    LabeledLine(label = itemLabel, value = itemName)
                    LabeledLine(label = amountLabel, value = amount)
                    LabeledLine(label = dateLabel, value = date)
                }
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
