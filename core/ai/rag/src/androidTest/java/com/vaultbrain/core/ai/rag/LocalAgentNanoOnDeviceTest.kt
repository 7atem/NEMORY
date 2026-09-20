package com.vaultbrain.core.ai.rag

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultbrain.core.ai.llm.AppForegroundChecker
import com.vaultbrain.core.ai.llm.MlKitNanoRuntime
import com.vaultbrain.core.ai.llm.NanoPromptClient
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAgentNanoOnDeviceTest {

    @Test
    fun retrieveRunsAgainstGeminiNano() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Instrumented tests never drive ProcessLifecycleOwner to STARTED, so the
        // production foreground checker would always report backgrounded. The gate is
        // bypassed here; Nano availability itself is still enforced below.
        val client = NanoPromptClient(MlKitNanoRuntime(), AppForegroundChecker { true })
        // isAvailable() starts UNKNOWN until a status refresh, so refresh once first.
        val capabilities = client.capabilities()
        if (!client.isAvailable()) {
            Log.i(TAG, "Nano unavailable — requires AICore device " +
                "(status=${capabilities.modelStatus}, package=${context.packageName})")
            return@runBlocking
        }

        val items = listOf(
            VaultItem(
                id = "passport",
                title = "Passport renewal receipt",
                sourceType = SourceType.TEXT_PASTE,
                rawOcrText = "Passport renewal fee of 110.00 paid on 2026-05-12 at the consulate."
            ),
            VaultItem(
                id = "insurance",
                title = "Car insurance policy",
                sourceType = SourceType.TEXT_PASTE,
                rawOcrText = "Comprehensive car insurance policy valid until 2027-01-31."
            ),
            VaultItem(
                id = "lab",
                title = "Blood lab results",
                sourceType = SourceType.TEXT_PASTE,
                rawOcrText = "Complete blood count from 2026-08-02, all values within range."
            )
        )
        val startedAt = System.nanoTime()
        val result = LocalAgent(client).retrieve(
            question = "When did I renew my passport?",
            initial = items
        ) { query ->
            if (query.contains("passport", ignoreCase = true)) listOf(items.first()) else emptyList()
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        Log.i(TAG, "LocalAgent+Nano: ${result.sources.size} sources, " +
            "${result.trace.size} tool/search calls in ${elapsedMs}ms")
        assertTrue("tool/search calls not bounded: ${result.trace.size}", result.trace.size <= 6)
        assertTrue("exceeded 60s wall-clock budget: ${elapsedMs}ms", elapsedMs < 60_000)
    }

    private companion object {
        const val TAG = "LocalAgentNanoTest"
    }
}
