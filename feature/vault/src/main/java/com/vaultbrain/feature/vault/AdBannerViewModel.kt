package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import com.vaultbrain.core.billing.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Minimal ViewModel that exposes [AdManager] to Compose banner placements.
 *
 * This avoids threading AdManager through every screen and keeps ad logic
 * centralized. The banner itself decides whether to render based on consent,
 * entitlement, and SDK initialization state.
 */
@HiltViewModel
class AdBannerViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel()
