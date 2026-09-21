package com.vaultbrain.sync.gmail

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UnconfiguredGmailDataSourceTest {

    private val dataSource = UnconfiguredGmailDataSource()

    @Test
    fun `reports not configured with no authorized accounts`() = runBlocking {
        assertThat(dataSource.isConfigured()).isFalse()
        assertThat(dataSource.authorizedAccounts()).isEmpty()
    }

    @Test
    fun `authorize fails instead of silently connecting`() = runBlocking {
        val result = dataSource.authorize("user@example.com")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `sync page fabricates no messages or deletions`() = runBlocking {
        val page = dataSource.page("user@example.com", 0L, GmailConnector.MAX_MESSAGES_PER_SYNC, null)

        assertThat(page.messages).isEmpty()
        assertThat(page.deletedIds).isEmpty()
    }

    @Test
    fun `open returns false`() {
        assertThat(dataSource.open("https://mail.google.com/mail/")).isFalse()
    }
}
