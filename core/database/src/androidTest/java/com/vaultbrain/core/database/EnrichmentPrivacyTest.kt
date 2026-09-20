package com.vaultbrain.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultbrain.shared.model.EnrichmentState
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.database.entity.VaultItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnrichmentPrivacyTest {
    @Test fun bulkReenrichmentPreservesExplicitPrivacySkip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VaultDatabase::class.java).build()
        try {
            val dao = db.vaultItemDao()
            val skipped = VaultItemEntity(id = "private", title = "No enrichment", sourceType = SourceType.APP_SHARE,
                parsedMetadata = "{}", lensTags = "[]", enrichmentState = EnrichmentState.SKIPPED_PRIVACY)
            dao.insert(skipped)
            dao.insert(skipped.copy(id = "eligible", enrichmentState = EnrichmentState.COMPLETE))
            assertEquals(1, dao.queueAllForLocalAiEnrichment(10L))
            assertEquals(EnrichmentState.SKIPPED_PRIVACY, dao.getById("private")?.enrichmentState)
            assertEquals(EnrichmentState.PENDING, dao.getById("eligible")?.enrichmentState)
            assertEquals(0, dao.claimEnrichment("private", 20L, 3))
        } finally {
            db.close()
        }
    }
}
