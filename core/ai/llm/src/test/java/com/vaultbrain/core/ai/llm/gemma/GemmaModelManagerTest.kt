package com.vaultbrain.core.ai.llm.gemma

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class GemmaModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val modelBytes = "fake-gemma-model".toByteArray()
    private val modelSha = sha256(modelBytes)

    @Test
    fun `missing model file resolves to NOT_DOWNLOADED`() {
        val missing = File(tempFolder.root, "models/gemma3-1b-it-int4.task")

        assertThat(GemmaModelFileVerifier.resolveStatus(true, missing, modelSha))
            .isEqualTo(GemmaModelStatus.NOT_DOWNLOADED)
    }

    @Test
    fun `unsupported device resolves to NOT_SUPPORTED even with a ready file`() {
        val model = tempFolder.newFile("gemma3-1b-it-int4.task").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(false, model, modelSha))
            .isEqualTo(GemmaModelStatus.NOT_SUPPORTED)
    }

    @Test
    fun `matching hash resolves to READY`() {
        val model = tempFolder.newFile("gemma3-1b-it-int4.task").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(GemmaModelStatus.READY)
    }

    @Test
    fun `hash mismatch resolves to ERROR`() {
        val model = tempFolder.newFile("gemma3-1b-it-int4.task").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, "0".repeat(64)))
            .isEqualTo(GemmaModelStatus.ERROR)
    }

    @Test
    fun `modified file after verification is re-hashed`() {
        val model = tempFolder.newFile("gemma3-1b-it-int4.task").apply { writeBytes(modelBytes) }
        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(GemmaModelStatus.READY)

        model.writeBytes("tampered".toByteArray())

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(GemmaModelStatus.ERROR)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
