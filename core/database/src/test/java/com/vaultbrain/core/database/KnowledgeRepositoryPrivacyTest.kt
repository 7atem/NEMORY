package com.vaultbrain.core.database

import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.KnowledgeGraphDao
import com.vaultbrain.core.database.entity.RelationshipEntity
import com.vaultbrain.core.database.repository.KnowledgeRepository
import com.vaultbrain.core.database.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRepositoryPrivacyTest {
    private val database = mockk<VaultDatabase>()
    private val dao = mockk<KnowledgeGraphDao>()
    private val repository = KnowledgeRepository(database, mockk<VaultRepository>())

    @After
    fun resetDecoy() = DecoySessionState.setDecoyMode(false)

    @Test
    fun `relationships are discarded if decoy starts during the database read`() = runTest {
        DecoySessionState.setDecoyMode(false)
        every { database.knowledgeGraphDao() } returns dao
        coEvery { dao.getRelationshipsForItem("a") } coAnswers {
            DecoySessionState.setDecoyMode(true)
            listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY"))
        }
        assertTrue(repository.getRelationships("a").isEmpty())
    }

    @Test
    fun `decoy relationships do not query the database`() = runTest {
        DecoySessionState.setDecoyMode(true)
        assertTrue(repository.getRelationships("a").isEmpty())
        coVerify(exactly = 0) { dao.getRelationshipsForItem(any()) }
    }
}
