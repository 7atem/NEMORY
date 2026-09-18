package com.vaultbrain.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Explains configured protection boundaries without claiming measured network traffic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScoreScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.privacy_overview_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.privacy_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(R.string.privacy_local_ai, R.string.privacy_storage, R.string.privacy_network,
                R.string.privacy_calendar, R.string.settings_gmail_unavailable).forEach { text ->
                Card(Modifier.fillMaxWidth()) { Text(stringResource(text), Modifier.padding(16.dp)) }
            }
        }
    }
}
