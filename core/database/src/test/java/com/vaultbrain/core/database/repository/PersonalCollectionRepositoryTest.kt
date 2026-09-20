package com.vaultbrain.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.PersonalCollectionSource
import com.vaultbrain.shared.database.dao.AuditLogDao
import com.vaultbrain.shared.database.dao.NotificationQueueDao
import com.vaultbrain.shared.database.dao.PersonalCollectionDao
import com.vaultbrain.shared.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.shared.database.dao.VaultItemDao
import com.vaultbrain.shared.database.entity.PersonalCollectionEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionMembershipEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PersonalCollectionRepositoryTest {
    private val collectionDao = mockk<PersonalCollectionDao>(relaxed = true)
    private val repository = VaultRepository(
        vaultItemDao = mockk<VaultItemDao>(),
        auditLogDao = mockk<AuditLogDao>(),
        notificationQueueDao = mockk<NotificationQueueDao>(),
        personalCollectionDao = collectionDao,
        personalCollectionSuggestionDao = mockk<PersonalCollectionSuggestionDao>(relaxed = true)
    )

    @Test
    fun `create preserves user terminology and uses USER source`() = runTest {
        val inserted = slot<PersonalCollectionEntity>()

        val result = repository.createCollection("  My Italy Adventure  ", now = 42L)

        coVerify { collectionDao.insertCollection(capture(inserted)) }
        assertThat(result.name).isEqualTo("My Italy Adventure")
        assertThat(inserted.captured.name).isEqualTo("My Italy Adventure")
        assertThat(inserted.captured.source).isEqualTo(PersonalCollectionSource.USER)
        assertThat(inserted.captured.createdAt).isEqualTo(42L)
    }

    @Test
    fun `rename pin archive and restore delegate without changing taxonomy`() = runTest {
        coEvery { collectionDao.rename(any(), any(), any()) } returns 1
        coEvery { collectionDao.setPinned(any(), any(), any()) } returns 1
        coEvery { collectionDao.setArchived(any(), any(), any()) } returns 1

        assertThat(repository.renameCollection("c1", "Bambu H2D")).isTrue()
        assertThat(repository.setCollectionPinned("c1", true)).isTrue()
        assertThat(repository.archiveCollection("c1")).isTrue()
        assertThat(repository.restoreCollection("c1")).isTrue()

        coVerify { collectionDao.rename("c1", "Bambu H2D", any()) }
        coVerify { collectionDao.setPinned("c1", true, any()) }
        coVerify { collectionDao.setArchived("c1", match { it != null }, any()) }
        coVerify { collectionDao.setArchived("c1", null, any()) }
    }

    @Test
    fun `adding item to multiple collections never removes another membership`() = runTest {
        coEvery { collectionDao.insertMembership(any()) } returns 1L

        assertThat(repository.addItemToCollection("item", "europe")).isTrue()
        assertThat(repository.addItemToCollection("item", "bookings")).isTrue()

        coVerify(exactly = 1) {
            collectionDao.insertMembership(match<PersonalCollectionMembershipEntity> {
                it.itemId == "item" && it.collectionId == "europe"
            })
        }
        coVerify(exactly = 1) {
            collectionDao.insertMembership(match<PersonalCollectionMembershipEntity> {
                it.itemId == "item" && it.collectionId == "bookings"
            })
        }
        coVerify(exactly = 0) { collectionDao.removeMembership(any(), any()) }
    }

    @Test
    fun `duplicate membership is reported as unchanged`() = runTest {
        coEvery { collectionDao.insertMembership(any()) } returns -1L

        assertThat(repository.addItemToCollection("item", "collection")).isFalse()
    }

    @Test
    fun `remove deletes only the selected membership`() = runTest {
        coEvery { collectionDao.removeMembership("collection", "item") } returns 1

        assertThat(repository.removeItemFromCollection("item", "collection")).isTrue()

        coVerify(exactly = 1) { collectionDao.removeMembership("collection", "item") }
    }
}
