package com.vaultbrain.core.database

import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.shared.database.dao.KnowledgeGraphDao
import com.vaultbrain.shared.database.entity.RelationshipEntity
import com.vaultbrain.core.database.repository.KnowledgeRepository
import com.vaultbrain.core.database.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRepositoryRelationshipsTest {
    private val database = mockk<VaultDatabase>()
    private val dao = mockk<KnowledgeGraphDao>()
    private val vault = mockk<VaultRepository>()
    private val repository = KnowledgeRepository(database, vault)

    private val item = VaultItem(id = "a", title = "Item A", rawOcrText = "Policy #POL-99201 expires 2026-12-14")

    @After
    fun resetDecoy() = DecoySessionState.setDecoyMode(false)

    private fun stubGraph(source: VaultItem = item, endpoints: List<VaultItem> = listOf(VaultItem(id = "b", title = "Item B"))) {
        every { database.knowledgeGraphDao() } returns dao
        coEvery { vault.getById(source.id) } returns source
        coEvery { vault.getByIds(any()) } answers {
            endpoints.filter { it.id in firstArg<List<String>>() }
        }
    }

    @Test
    fun `decoy replaceRelationships does not touch the database`() = runTest {
        DecoySessionState.setDecoyMode(true)
        repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY")))
        coVerify(exactly = 0) { vault.getById(any()) }
        coVerify(exactly = 0) { dao.replaceRelationshipsInvolving(any(), any()) }
    }

    @Test
    fun `archived or stealth source items reject relationship writes`() = runTest {
        stubGraph(source = item.copy(isArchived = true))
        repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY")))
        stubGraph(source = item.copy(isStealth = true))
        repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY")))
        coVerify(exactly = 0) { dao.replaceRelationshipsInvolving(any(), any()) }
    }

    @Test
    fun `invalid relationship type is rejected`() {
        stubGraph()
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "NOT_A_TYPE"))) }
        }
    }

    @Test
    fun `relationships must involve the source item`() {
        stubGraph()
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.replaceRelationships("a", listOf(RelationshipEntity("r", "b", "b", "SAME_ENTITY"))) }
        }
    }

    @Test
    fun `evidence must appear verbatim in the source ocr text`() {
        stubGraph()
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY", evidence = "not in the ocr text")))
            }
        }
    }

    @Test
    fun `archived or stealth endpoints are rejected`() {
        stubGraph(endpoints = listOf(VaultItem(id = "b", title = "Item B", isArchived = true)))
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY"))) }
        }
        stubGraph(endpoints = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.replaceRelationships("a", listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY"))) }
        }
    }

    @Test
    fun `more than 24 relationships are rejected`() {
        stubGraph()
        val relationships = (1..25).map { RelationshipEntity("r$it", "a", "b", "SAME_ENTITY") }
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.replaceRelationships("a", relationships) }
        }
    }

    @Test
    fun `replace removes previous relationships and coerces confidence`() = runTest {
        stubGraph(endpoints = listOf(VaultItem(id = "b", title = "Item B"), VaultItem(id = "c", title = "Item C")))
        val stored = mutableMapOf<String, RelationshipEntity>()
        coEvery { dao.replaceRelationshipsInvolving(any(), any()) } answers {
            val itemId = firstArg<String>()
            stored.values.removeAll { it.sourceItemId == itemId || it.targetItemId == itemId }
            secondArg<List<RelationshipEntity>>().forEach { stored[it.id] = it }
        }

        repository.replaceRelationships("a", listOf(RelationshipEntity("r1", "a", "b", "SAME_ENTITY", evidence = "Policy #POL-99201")))
        assertEquals(setOf("r1"), stored.keys)

        repository.replaceRelationships("a", listOf(RelationshipEntity("r2", "a", "c", "VERSION_OF", confidence = 2.5f)))
        assertEquals(setOf("r2"), stored.keys)
        assertEquals(1.0f, stored.getValue("r2").confidence)
    }

    @Test
    fun `batch relationships are discarded if decoy starts during the database read`() = runTest {
        DecoySessionState.setDecoyMode(false)
        every { database.knowledgeGraphDao() } returns dao
        coEvery { dao.getRelationshipsForItems(listOf("a", "b")) } coAnswers {
            DecoySessionState.setDecoyMode(true)
            listOf(RelationshipEntity("r", "a", "b", "SAME_ENTITY"))
        }
        assertTrue(repository.relationshipsForItems(listOf("a", "b")).isEmpty())
    }
}
