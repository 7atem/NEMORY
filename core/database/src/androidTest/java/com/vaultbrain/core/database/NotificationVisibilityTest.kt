package com.vaultbrain.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.database.entity.NotificationQueueEntity
import com.vaultbrain.shared.database.entity.VaultItemEntity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationVisibilityTest {
    @Test fun concurrentClaimsAndHiddenTargets() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VaultDatabase::class.java).build()
        try {
            val item = VaultItemEntity(id = "a", title = "Private fixture", sourceType = SourceType.MANUAL, parsedMetadata = "{}", lensTags = "[]")
            db.vaultItemDao().insert(item)
            val dao = db.notificationQueueDao()
            val alert = NotificationQueueEntity("n", "a", "VAULT_ITEM", 1, "REMINDER", "Private", "Private")
            dao.insert(alert)
            val claims = (1..8).map { async(Dispatchers.IO) { dao.claimVisible("n", 1, 2) } }.awaitAll()
            assertEquals(1, claims.sum())
            dao.snoozeById("n", 10)
            assertEquals(0, dao.claimVisible("n", 1, 20))
            db.vaultItemDao().insert(item.copy(isStealth = true))
            assertEquals(0, dao.claimVisible("n", 10, 20))
            db.vaultItemDao().insert(item.copy(isArchived = true))
            assertEquals(0, dao.claimVisible("n", 10, 20))
            db.vaultItemDao().deleteById("a")
            assertEquals(0, dao.claimVisible("n", 10, 20))
        } finally { db.close() }
    }
}
