package com.vaultbrain.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.CollectionSuggestionStatus
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.AuditLogDao
import com.vaultbrain.core.database.dao.NotificationQueueDao
import com.vaultbrain.core.database.dao.PersonalCollectionDao
import com.vaultbrain.core.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.core.database.dao.VaultItemDao
import com.vaultbrain.core.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.core.database.entity.PersonalCollectionSuggestionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class PersonalCollectionSuggestionRepositoryTest {
    private val suggestionDao = mockk<PersonalCollectionSuggestionDao>(relaxed = true)
    private val repository = VaultRepository(
        vaultItemDao = mockk<VaultItemDao>(),
        auditLogDao = mockk<AuditLogDao>(),
        notificationQueueDao = mockk<NotificationQueueDao>(),
        personalCollectionDao = mockk<PersonalCollectionDao>(),
        personalCollectionSuggestionDao = suggestionDao
    )

    @After
    fun tearDown() {
        DecoySessionState.setDecoyMode(false)
    }

    private fun entity(
        status: CollectionSuggestionStatus,
        confidence: Float = 0.8f
    ) = PersonalCollectionSuggestionEntity(
        collectionId = "c1", itemId = "i1", confidence = confidence,
        status = status, createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun `upsert inserts a new suggestion`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns null
        val captured = slot<PersonalCollectionSuggestionEntity>()
        coEvery { suggestionDao.insert(capture(captured)) } returns 1L

        assertThat(repository.upsertSuggestion("i1", "c1", 0.9f)).isTrue()
        assertThat(captured.captured.status).isEqualTo(CollectionSuggestionStatus.SUGGESTED)
        assertThat(captured.captured.confidence).isEqualTo(0.9f)
    }

    @Test
    fun `upsert refreshes a pending suggestion`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns entity(CollectionSuggestionStatus.SUGGESTED, 0.6f)
        coEvery {
            suggestionDao.update("c1", "i1", 0.9f, CollectionSuggestionStatus.SUGGESTED, any())
        } returns 1

        assertThat(repository.upsertSuggestion("i1", "c1", 0.9f)).isTrue()
        coVerify { suggestionDao.update("c1", "i1", 0.9f, CollectionSuggestionStatus.SUGGESTED, any()) }
    }

    @Test
    fun `upsert never revives a rejected pair`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns entity(CollectionSuggestionStatus.REJECTED)

        assertThat(repository.upsertSuggestion("i1", "c1", 0.99f)).isFalse()
        coVerify(exactly = 0) { suggestionDao.insert(any()) }
        coVerify(exactly = 0) { suggestionDao.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `upsert never downgrades an accepted pair`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns entity(CollectionSuggestionStatus.ACCEPTED)

        assertThat(repository.upsertSuggestion("i1", "c1", 0.99f)).isFalse()
        coVerify(exactly = 0) { suggestionDao.insert(any()) }
        coVerify(exactly = 0) { suggestionDao.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `accept creates membership and marks accepted atomically`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns entity(CollectionSuggestionStatus.SUGGESTED)

        assertThat(repository.acceptSuggestion("i1", "c1")).isTrue()
        coVerify {
            suggestionDao.accept(
                "c1", "i1",
                match<PersonalCollectionMembershipEntity> { it.collectionId == "c1" && it.itemId == "i1" },
                any()
            )
        }
    }

    @Test
    fun `accept on unknown suggestion does nothing`() = runTest {
        coEvery { suggestionDao.get("c1", "i1") } returns null

        assertThat(repository.acceptSuggestion("i1", "c1")).isFalse()
        coVerify(exactly = 0) { suggestionDao.accept(any(), any(), any(), any()) }
    }

    @Test
    fun `reject persists suppression evidence`() = runTest {
        coEvery {
            suggestionDao.setStatus("c1", "i1", CollectionSuggestionStatus.REJECTED, any())
        } returns 1

        assertThat(repository.rejectSuggestion("i1", "c1")).isTrue()
        coVerify { suggestionDao.setStatus("c1", "i1", CollectionSuggestionStatus.REJECTED, any()) }
        coVerify(exactly = 0) { suggestionDao.insert(any()) }
    }

    @Test
    fun `decoy mode hides and blocks suggestions`() = runTest {
        DecoySessionState.setDecoyMode(true)

        assertThat(repository.getAllSuggestionsForItem("i1")).isEmpty()
        assertThat(repository.getActiveCollections()).isEmpty()
        assertThat(repository.upsertSuggestion("i1", "c1", 0.9f)).isFalse()
        assertThat(repository.acceptSuggestion("i1", "c1")).isFalse()
        assertThat(repository.rejectSuggestion("i1", "c1")).isFalse()
        coVerify(exactly = 0) { suggestionDao.get(any(), any()) }
    }
}
