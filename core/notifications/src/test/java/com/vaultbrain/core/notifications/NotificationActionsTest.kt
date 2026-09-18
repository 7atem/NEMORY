package com.vaultbrain.core.notifications

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.vaultbrain.core.database.dao.NotificationQueueDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationActionsTest {
    private val dao = mockk<NotificationQueueDao>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val manager = UnifiedAlertManager(mockk<Context>(), workManager, dao)

    @Test
    fun `dismiss removes only the selected notification rather than all document alerts`() = runTest {
        manager.dismissNotification("notification-1")
        coVerify(exactly = 1) { dao.deleteById("notification-1") }
        coVerify(exactly = 0) { dao.deleteForTarget(any()) }
        verify { workManager.cancelUniqueWork("AlertWorker:notification-1") }
    }

    @Test
    fun `snooze resets delivery and schedules the same notification for tomorrow`() = runTest {
        val trigger = slot<Long>()
        coEvery { dao.snoozeById("notification-1", capture(trigger)) } returns 1
        val before = System.currentTimeMillis()
        manager.snoozeNotification("notification-1")
        val after = System.currentTimeMillis()
        val delay = TimeUnit.HOURS.toMillis(24)
        assertTrue(trigger.captured in (before + delay)..(after + delay))
        val request = slot<OneTimeWorkRequest>()
        verify { workManager.enqueueUniqueWork("AlertWorker:notification-1", ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals("notification-1", request.captured.workSpec.input.getString("notification_id"))
        assertEquals(delay, request.captured.workSpec.initialDelay)
    }

    @Test
    fun `snooze cannot recreate a deleted notification`() = runTest {
        coEvery { dao.snoozeById("deleted", any()) } returns 0
        manager.snoozeNotification("deleted")
        coVerify { dao.snoozeById("deleted", any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }
}
