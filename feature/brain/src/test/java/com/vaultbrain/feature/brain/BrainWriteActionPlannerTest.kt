package com.vaultbrain.feature.brain

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BrainWriteActionPlannerTest {
    private val repository = mockk<VaultRepository>()
    private val alertManager = mockk<UnifiedAlertManager>()
    private lateinit var planner: BrainWriteActionPlanner
    private val now = Instant.parse("2026-08-24T10:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")

    @Before
    fun setUp() {
        planner = BrainWriteActionPlanner(repository, alertManager)
    }

    @Test
    fun `ordinary question is not treated as a write`() = runTest {
        val result = planner.plan("What is on my watchlist?", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.NotWriteAction)
        coVerify(exactly = 0) { repository.getActive() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `pin planning resolves exact active title without writing`() = runTest {
        val dune = item("dune", "Dune")
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("Pin Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(dune, dune.updatedAt, BrainWriteAction.Pin, "Pin Dune")
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { alertManager.scheduleAlerts(any()) }
    }

    @Test
    fun `unpin planning resolves a pinned item without writing`() = runTest {
        val dune = item("dune", "Dune").copy(isPinned = true)
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("Unpin Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(dune, dune.updatedAt, BrainWriteAction.Unpin, "Unpin Dune")
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `arabic clear reminder planning preserves the no-write preview invariant`() = runTest {
        val dune = item("dune", "الكثيب").copy(secondaryAlertDate = now + 3_600_000L)
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("ألغِ تذكير الكثيب", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(
                    dune,
                    dune.updatedAt,
                    BrainWriteAction.ClearReminder,
                    "ألغِ تذكير الكثيب"
                )
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { alertManager.cancelForItem(any()) }
    }

    @Test
    fun `unpin rejects an item that is not pinned`() = runTest {
        coEvery { repository.getActive() } returns listOf(item("dune", "Dune"))

        val result = planner.plan("Unpin Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `clear reminder rejects an item without a reminder`() = runTest {
        coEvery { repository.getActive() } returns listOf(item("dune", "Dune"))

        val result = planner.plan("Clear reminder for Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { alertManager.cancelForItem(any()) }
    }

    @Test
    fun `ambiguous partial title fails closed`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            item("dune", "Dune"),
            item("dune-two", "Dune Part Two")
        )

        val result = planner.plan("Pin Dun", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Rejected(BrainWriteIssue.AMBIGUOUS_MATCH)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `english reminder resolves tomorrow and explicit time`() = runTest {
        val dune = item("dune", "Dune")
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("Remind me tomorrow at 18:30 about Dune", now, zone)

        val ready = result as BrainWritePlanResult.Ready
        val reminder = ready.preview.action as BrainWriteAction.SetReminder
        assertThat(reminder.triggerAt).isEqualTo(
            Instant.parse("2026-08-25T18:30:00Z").toEpochMilli()
        )
    }

    @Test
    fun `arabic reminder resolves tomorrow and explicit time`() = runTest {
        val dune = item("dune", "الكثيب")
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("ذكّرني غدًا الساعة 18:30 بشأن الكثيب", now, zone)

        val ready = result as BrainWritePlanResult.Ready
        val reminder = ready.preview.action as BrainWriteAction.SetReminder
        assertThat(reminder.triggerAt).isEqualTo(
            Instant.parse("2026-08-25T18:30:00Z").toEpochMilli()
        )
    }

    @Test
    fun `invalid reminder time is rejected before repository access`() = runTest {
        val result = planner.plan("Remind me tomorrow at 25:00 about Dune", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.Rejected(BrainWriteIssue.INVALID_DATE))
        coVerify(exactly = 0) { repository.getActive() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `pin executes only against unchanged item snapshot`() = runTest {
        val original = item("dune", "Dune")
        val preview = BrainWritePreview(original, original.updatedAt, BrainWriteAction.Pin, "Pin Dune")
        val saved = slot<VaultItem>()
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(capture(saved)) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.isPinned).isTrue()
        assertThat(saved.captured.userEditedAt).isEqualTo(now)
        coVerify(exactly = 0) { alertManager.scheduleAlerts(any()) }
    }

    @Test
    fun `unpin executes only after confirmation and does not touch alerts`() = runTest {
        val original = item("dune", "Dune").copy(isPinned = true)
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.Unpin,
            "Unpin Dune"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(capture(saved)) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.isPinned).isFalse()
        assertThat(saved.captured.userEditedAt).isEqualTo(now)
        coVerify(exactly = 0) { alertManager.cancelForItem(any()) }
        coVerify(exactly = 0) { alertManager.scheduleAlerts(any()) }
    }

    @Test
    fun `clear reminder persists null and cancels its alert`() = runTest {
        val original = item("dune", "Dune").copy(
            secondaryAlertDate = now + 3_600_000L
        )
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.ClearReminder,
            "Clear reminder for Dune"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(capture(saved)) } just Runs
        coEvery { alertManager.cancelForItem(original.id) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.secondaryAlertDate).isNull()
        coVerify(exactly = 1) { alertManager.cancelForItem(original.id) }
    }

    @Test
    fun `clear reminder cancellation failure restores the item and alert`() = runTest {
        val original = item("dune", "Dune").copy(
            secondaryAlertDate = now + 3_600_000L
        )
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.ClearReminder,
            "Clear reminder for Dune"
        )
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(any()) } just Runs
        coEvery { alertManager.cancelForItem(original.id) } throws
            IllegalStateException("scheduler")
        coEvery { alertManager.scheduleAlerts(original) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isEqualTo(
            BrainWriteExecutionResult.Failed(BrainWriteIssue.EXECUTION_FAILED)
        )
        coVerify(exactly = 1) { repository.save(match { it.secondaryAlertDate == null }) }
        coVerify(exactly = 1) { repository.save(original) }
        coVerify(exactly = 1) { alertManager.scheduleAlerts(original) }
    }

    @Test
    fun `changed item rejects execution without writing`() = runTest {
        val original = item("dune", "Dune")
        val preview = BrainWritePreview(original, original.updatedAt, BrainWriteAction.Pin, "Pin Dune")
        coEvery { repository.getById(original.id) } returns original.copy(updatedAt = 101L)

        val result = planner.execute(preview, now)

        assertThat(result).isEqualTo(
            BrainWriteExecutionResult.Failed(BrainWriteIssue.ITEM_CHANGED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `reminder saves item and schedules alert`() = runTest {
        val original = item("dune", "Dune")
        val triggerAt = now + 60L * 60L * 1000L
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.SetReminder(triggerAt),
            "Remind me today at 11:00 about Dune"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(capture(saved)) } just Runs
        coEvery { alertManager.scheduleAlerts(any()) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.secondaryAlertDate).isEqualTo(triggerAt)
        coVerify(exactly = 1) { alertManager.scheduleAlerts(saved.captured) }
    }

    @Test
    fun `alert scheduling failure rolls item back`() = runTest {
        val original = item("dune", "Dune")
        val triggerAt = now + 60L * 60L * 1000L
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.SetReminder(triggerAt),
            "Remind me today at 11:00 about Dune"
        )
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(any()) } just Runs
        coEvery { alertManager.scheduleAlerts(any()) } throws IllegalStateException("scheduler")

        val result = planner.execute(preview, now)

        assertThat(result).isEqualTo(
            BrainWriteExecutionResult.Failed(BrainWriteIssue.EXECUTION_FAILED)
        )
        coVerify(exactly = 1) { repository.save(match { it.secondaryAlertDate == triggerAt }) }
        coVerify(exactly = 1) { repository.save(original) }
    }

    @Test
    fun `watch question is not mistaken for completion command`() = runTest {
        val result = planner.plan("Have I watched Dune?", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.NotWriteAction)
        coVerify(exactly = 0) { repository.getActive() }
    }

    @Test
    fun `movie can be planned as watched without writing`() = runTest {
        val dune = item("dune", "Dune", Classification.MOVIE)
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("Mark Dune as watched", now, zone)

        val ready = result as BrainWritePlanResult.Ready
        assertThat(ready.preview.action).isEqualTo(
            BrainWriteAction.MarkMediaCompleted(MediaCompletionKind.WATCHED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `arabic book completion uses finished status`() = runTest {
        val book = item("dune", "الكثيب", Classification.BOOK)
        coEvery { repository.getActive() } returns listOf(book)

        val result = planner.plan("قرأت الكثيب", now, zone)

        val ready = result as BrainWritePlanResult.Ready
        assertThat(ready.preview.action).isEqualTo(
            BrainWriteAction.MarkMediaCompleted(MediaCompletionKind.FINISHED)
        )
    }

    @Test
    fun `completion command rejects wrong media category`() = runTest {
        val book = item("dune", "Dune", Classification.BOOK)
        coEvery { repository.getActive() } returns listOf(book)

        val result = planner.plan("Mark Dune as watched", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Rejected(BrainWriteIssue.CATEGORY_MISMATCH)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `already completed media item is rejected`() = runTest {
        val movie = item("dune", "Dune", Classification.MOVIE).copy(
            customFields = mapOf("media_status" to "watched")
        )
        coEvery { repository.getActive() } returns listOf(movie)

        val result = planner.plan("I watched Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `media completion writes canonical status and clears reminder`() = runTest {
        val movie = item("dune", "Dune", Classification.MOVIE).copy(
            secondaryAlertDate = now + 60L * 60L * 1000L
        )
        val preview = BrainWritePreview(
            movie,
            movie.updatedAt,
            BrainWriteAction.MarkMediaCompleted(MediaCompletionKind.WATCHED),
            "Mark Dune as watched"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(movie.id) } returns movie
        coEvery { repository.save(capture(saved)) } just Runs
        coEvery { alertManager.cancelForItem(movie.id) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.parsedMetadata["media_status"]).isEqualTo("watched")
        assertThat(saved.captured.customFields["media_status"]).isEqualTo("watched")
        assertThat(saved.captured.secondaryAlertDate).isNull()
        coVerify(exactly = 1) { alertManager.cancelForItem(movie.id) }
    }

    @Test
    fun `alert cancellation failure restores prior media item and alerts`() = runTest {
        val movie = item("dune", "Dune", Classification.MOVIE).copy(
            secondaryAlertDate = now + 60L * 60L * 1000L
        )
        val preview = BrainWritePreview(
            movie,
            movie.updatedAt,
            BrainWriteAction.MarkMediaCompleted(MediaCompletionKind.WATCHED),
            "Mark Dune as watched"
        )
        coEvery { repository.getById(movie.id) } returns movie
        coEvery { repository.save(any()) } just Runs
        coEvery { alertManager.cancelForItem(movie.id) } throws IllegalStateException("scheduler")
        coEvery { alertManager.scheduleAlerts(movie) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isEqualTo(
            BrainWriteExecutionResult.Failed(BrainWriteIssue.EXECUTION_FAILED)
        )
        coVerify(exactly = 1) {
            repository.save(match { it.customFields["media_status"] == "watched" })
        }
        coVerify(exactly = 1) { repository.save(movie) }
        coVerify(exactly = 1) { alertManager.scheduleAlerts(movie) }
    }

    @Test
    fun `english archive planning resolves active title without writing`() = runTest {
        val dune = item("dune", "Dune")
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("Archive Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(dune, dune.updatedAt, BrainWriteAction.Archive, "Archive Dune")
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `arabic archive planning preserves the no-write preview invariant`() = runTest {
        val dune = item("dune", "الكثيب")
        coEvery { repository.getActive() } returns listOf(dune)

        val result = planner.plan("أرشف الكثيب", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(dune, dune.updatedAt, BrainWriteAction.Archive, "أرشف الكثيب")
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `archive question is not mistaken for a write command`() = runTest {
        val result = planner.plan("Should I archive Dune?", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.NotWriteAction)
        coVerify(exactly = 0) { repository.getActive() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `archive executes after confirmation and preserves reminder settings`() = runTest {
        val reminderAt = now + 60L * 60L * 1000L
        val original = item("dune", "Dune").copy(secondaryAlertDate = reminderAt)
        val preview = BrainWritePreview(
            original,
            original.updatedAt,
            BrainWriteAction.Archive,
            "Archive Dune"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(original.id) } returns original
        coEvery { repository.save(capture(saved)) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.isArchived).isTrue()
        assertThat(saved.captured.secondaryAlertDate).isEqualTo(reminderAt)
        assertThat(saved.captured.userEditedAt).isEqualTo(now)
        coVerify(exactly = 0) { alertManager.cancelForItem(any()) }
        coVerify(exactly = 0) { alertManager.scheduleAlerts(any()) }
    }

    @Test
    fun `english restore planning searches archived items without writing`() = runTest {
        val dune = item("dune", "Dune").copy(isArchived = true)
        coEvery { repository.getArchived() } returns listOf(dune)

        val result = planner.plan("Restore Dune", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(dune, dune.updatedAt, BrainWriteAction.Restore, "Restore Dune")
            )
        )
        coVerify(exactly = 0) { repository.getActive() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `arabic restore planning resolves an archived title`() = runTest {
        val dune = item("dune", "الكثيب").copy(isArchived = true)
        coEvery { repository.getArchived() } returns listOf(dune)

        val result = planner.plan("استرجع الكثيب من الأرشيف", now, zone)

        assertThat(result).isEqualTo(
            BrainWritePlanResult.Ready(
                BrainWritePreview(
                    dune,
                    dune.updatedAt,
                    BrainWriteAction.Restore,
                    "استرجع الكثيب من الأرشيف"
                )
            )
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `restore never falls back to matching an active item`() = runTest {
        coEvery { repository.getArchived() } returns emptyList()

        val result = planner.plan("Restore Dune", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.Rejected(BrainWriteIssue.NO_MATCH))
        coVerify(exactly = 0) { repository.getActive() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `restore question is not mistaken for a write command`() = runTest {
        val result = planner.plan("Can I restore Dune?", now, zone)

        assertThat(result).isEqualTo(BrainWritePlanResult.NotWriteAction)
        coVerify(exactly = 0) { repository.getArchived() }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `restore executes after confirmation and preserves reminder settings`() = runTest {
        val reminderAt = now + 60L * 60L * 1000L
        val archived = item("dune", "Dune").copy(
            isArchived = true,
            secondaryAlertDate = reminderAt
        )
        val preview = BrainWritePreview(
            archived,
            archived.updatedAt,
            BrainWriteAction.Restore,
            "Restore Dune"
        )
        val saved = slot<VaultItem>()
        coEvery { repository.getById(archived.id) } returns archived
        coEvery { repository.save(capture(saved)) } just Runs

        val result = planner.execute(preview, now)

        assertThat(result).isInstanceOf(BrainWriteExecutionResult.Success::class.java)
        assertThat(saved.captured.isArchived).isFalse()
        assertThat(saved.captured.secondaryAlertDate).isEqualTo(reminderAt)
        assertThat(saved.captured.userEditedAt).isEqualTo(now)
        coVerify(exactly = 0) { alertManager.cancelForItem(any()) }
        coVerify(exactly = 0) { alertManager.scheduleAlerts(any()) }
    }

    @Test
    fun `restore rejects execution when item is no longer archived`() = runTest {
        val archived = item("dune", "Dune").copy(isArchived = true)
        val preview = BrainWritePreview(
            archived,
            archived.updatedAt,
            BrainWriteAction.Restore,
            "Restore Dune"
        )
        coEvery { repository.getById(archived.id) } returns archived.copy(isArchived = false)

        val result = planner.execute(preview, now)

        assertThat(result).isEqualTo(
            BrainWriteExecutionResult.Failed(BrainWriteIssue.ITEM_CHANGED)
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    private fun item(
        id: String,
        title: String,
        classification: Classification? = null
    ) = VaultItem(
        id = id,
        title = title,
        createdAt = 50L,
        updatedAt = 100L,
        aiClassification = classification
    )
}
