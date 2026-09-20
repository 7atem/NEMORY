package com.vaultbrain.core.ai.llm

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.Test

class CloudConsentStoreTest {

    @Test
    fun `stored values load only remembered-safe categories`() {
        val fixture = fixture(
            stored = setOf(
                Classification.BOOK.name,
                Classification.PASSPORT.name,
                "REMOVED_ENUM_VALUE"
            )
        )

        assertThat(fixture.store.optedInCategories.value)
            .containsExactly(Classification.BOOK)
        assertThat(fixture.store.isOptedIn(Classification.PASSPORT)).isFalse()
    }

    @Test
    fun `remember refuses sensitive and per-request categories`() {
        val fixture = fixture()

        fixture.store.remember(
            setOf(Classification.MOVIE, Classification.RECEIPT, Classification.LAB_RESULT)
        )

        assertThat(fixture.store.optedInCategories.value)
            .containsExactly(Classification.MOVIE)
        verify {
            fixture.editor.putStringSet(
                "approved_categories",
                setOf(Classification.MOVIE.name)
            )
        }
    }

    @Test
    fun `approval can be revoked individually or cleared`() {
        val fixture = fixture()
        fixture.store.remember(setOf(Classification.BOOK, Classification.TV_SERIES))

        fixture.store.revoke(Classification.BOOK)
        assertThat(fixture.store.optedInCategories.value)
            .containsExactly(Classification.TV_SERIES)

        fixture.store.clear()
        assertThat(fixture.store.optedInCategories.value).isEmpty()
    }

    private fun fixture(stored: Set<String> = emptySet()): Fixture {
        val context = mockk<Context>()
        val applicationContext = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.applicationContext } returns applicationContext
        every { applicationContext.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences
        every { preferences.getStringSet(any(), any()) } returns stored
        every { preferences.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.apply() } just Runs
        return Fixture(
            store = SharedPreferencesCloudConsentStore(context, PrivacyEngine()),
            editor = editor
        )
    }

    private data class Fixture(
        val store: SharedPreferencesCloudConsentStore,
        val editor: SharedPreferences.Editor
    )
}
