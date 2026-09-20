package com.vaultbrain.core.notifications

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.shared.model.VaultReminderStatus
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.shared.database.dao.VaultReminderDao
import com.vaultbrain.shared.database.entity.VaultReminderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class VaultReminderManagerTest {
    private val dao = mockk<VaultReminderDao>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val manager = VaultReminderManager(dao, workManager)

    init {
        every { dao.observeActive() } returns flowOf(emptyList())
    }

    @After
    fun resetDecoy() = DecoySessionState.setDecoyMode(false)

    @Test
    fun `create persists and schedules reminder`() = runTest {
        coEvery { dao.insert(any()) } returns 1
        val reminder = VaultReminder(id = "r1", title = " Renew passport ", dueAt = System.currentTimeMillis() + 60_000)

        assertThat(manager.create(reminder)).isTrue()
        val saved = slot<VaultReminderEntity>()
        coVerify { dao.insert(capture(saved)) }
        assertThat(saved.captured.title).isEqualTo("Renew passport")
        verify {
            workManager.enqueueUniqueWork(
                VaultReminderManager.workName("r1"),
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `create rejects reminders in the past`() = runTest {
        val reminder = VaultReminder(id = "past", title = "Expired", dueAt = System.currentTimeMillis() - 1)

        assertThat(manager.create(reminder)).isFalse()
        coVerify(exactly = 0) { dao.insert(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `create does not duplicate an active item reminder for the same date`() = runTest {
        val dueAt = System.currentTimeMillis() + 60_000
        val existing = VaultReminderEntity(
            id = "existing",
            title = "Expiry",
            dueAt = dueAt,
            status = VaultReminderStatus.SCHEDULED,
            createdAt = 1,
            updatedAt = 1,
            vaultItemId = "item-1"
        )
        coEvery { dao.getActiveForVaultItemAt("item-1", dueAt) } returns existing

        assertThat(
            manager.create(
                VaultReminder(id = "new", title = "Expiry", dueAt = dueAt, vaultItemId = "item-1")
            )
        ).isTrue()
        coVerify(exactly = 0) { dao.insert(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `snooze changes due time and completion cancels work`() = runTest {
        val entity = VaultReminderEntity("r1", "Call", 1, VaultReminderStatus.SCHEDULED, createdAt = 1, updatedAt = 1)
        coEvery { dao.get("r1") } returns entity
        coEvery { dao.update(any()) } returns 1
        coEvery { dao.setStatus(any(), any(), any()) } returns 1

        assertThat(manager.snooze("r1", 5_000)).isTrue()
        val updated = slot<VaultReminderEntity>()
        coVerify { dao.update(capture(updated)) }
        assertThat(updated.captured.status).isEqualTo(VaultReminderStatus.SNOOZED)
        assertThat(updated.captured.dueAt).isEqualTo(5_000)

        assertThat(manager.complete("r1")).isTrue()
        coVerify { dao.setStatus("r1", VaultReminderStatus.COMPLETED, any()) }
        verify { workManager.cancelUniqueWork(VaultReminderManager.workName("r1")) }
    }

    @Test
    fun `decoy mode hides and rejects reminders`() = runTest {
        DecoySessionState.setDecoyMode(true)
        assertThat(manager.create(VaultReminder(id = "r1", title = "Private", dueAt = 1))).isFalse()
        assertThat(manager.get("r1")).isNull()
        coVerify(exactly = 0) { dao.insert(any()) }
    }
}
