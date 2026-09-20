package com.vaultbrain.feature.vault.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReviewInboxViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {
    val items: StateFlow<List<VaultItem>> = repository.observeNeedsReview().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun accept(item: VaultItem) {
        viewModelScope.launch { repository.save(ReviewResolution.accept(item)) }
    }
}
