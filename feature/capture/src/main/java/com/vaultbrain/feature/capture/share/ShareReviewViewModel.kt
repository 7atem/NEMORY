package com.vaultbrain.feature.capture.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.database.dao.PersonalCollectionDao
import com.vaultbrain.shared.database.dao.PersonalCollectionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the share review screen.
 *
 * Exposes the user's active personal collections so a share can be pre-targeted
 * to one of them before entering the capture pipeline.
 */
@HiltViewModel
class ShareReviewViewModel @Inject constructor(
    collectionDao: PersonalCollectionDao
) : ViewModel() {

    val collections: StateFlow<List<PersonalCollection>> = collectionDao.observeActive()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private fun PersonalCollectionRow.toDomain(): PersonalCollection = PersonalCollection(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        isPinned = isPinned,
        source = source,
        itemCount = itemCount
    )
}
