package com.vaultbrain.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.database.dao.AuditLogDao
import com.vaultbrain.shared.database.dao.NotificationQueueDao
import com.vaultbrain.shared.database.dao.PersonalCollectionDao
import com.vaultbrain.shared.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.shared.database.dao.VaultItemDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VaultRepositoryDuplicateTest {
    private val itemDao = mockk<VaultItemDao>()
    private val repository = VaultRepository(
        vaultItemDao = itemDao,
        auditLogDao = mockk<AuditLogDao>(),
        notificationQueueDao = mockk<NotificationQueueDao>(),
        personalCollectionDao = mockk<PersonalCollectionDao>(),
        personalCollectionSuggestionDao = mockk<PersonalCollectionSuggestionDao>(relaxed = true)
    )

    @Test
    fun `completes indexing and persists duplicate review evidence atomically`() = runTest {
        coEvery {
            itemDao.completeIndexing(
                id = "new-item",
                duplicateItemId = "earlier-item",
                similarity = 0.995f,
                updatedAt = any()
            )
        } returns 1

        val completed = repository.completeIndexing(
            id = "new-item",
            duplicateItemId = "earlier-item",
            similarity = 0.995f
        )

        assertThat(completed).isTrue()
        coVerify(exactly = 1) {
            itemDao.completeIndexing(
                id = "new-item",
                duplicateItemId = "earlier-item",
                similarity = 0.995f,
                updatedAt = any()
            )
        }
    }
}
