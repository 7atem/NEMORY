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

    private val modelBytes = "fake-qwen-model".toByteArray()
    private val modelSha = sha256(modelBytes)

    @Test
    fun `missing model file resolves to NOT_DOWNLOADED`() {
        val missing = File(tempFolder.root, "models/Qwen3VL-2B-Instruct-Q4_K_M.gguf")

        assertThat(GemmaModelFileVerifier.resolveStatus(true, missing, modelSha))
            .isEqualTo(OnDeviceModelStatus.NOT_DOWNLOADED)
    }

    @Test
    fun `unsupported device resolves to NOT_SUPPORTED even with a ready file`() {
        val model = tempFolder.newFile("Qwen3VL-2B-Instruct-Q4_K_M.gguf").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(false, model, modelSha))
            .isEqualTo(OnDeviceModelStatus.NOT_SUPPORTED)
    }

    @Test
    fun `matching hash resolves to READY`() {
        val model = tempFolder.newFile("Qwen3VL-2B-Instruct-Q4_K_M.gguf").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(OnDeviceModelStatus.READY)
    }

    @Test
    fun `hash mismatch resolves to ERROR`() {
        val model = tempFolder.newFile("Qwen3VL-2B-Instruct-Q4_K_M.gguf").apply { writeBytes(modelBytes) }

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, "0".repeat(64)))
            .isEqualTo(OnDeviceModelStatus.ERROR)
    }

    @Test
    fun `modified file after verification is re-hashed`() {
        val model = tempFolder.newFile("Qwen3VL-2B-Instruct-Q4_K_M.gguf").apply { writeBytes(modelBytes) }
        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(OnDeviceModelStatus.READY)

        model.writeBytes("tampered".toByteArray())

        assertThat(GemmaModelFileVerifier.resolveStatus(true, model, modelSha))
            .isEqualTo(OnDeviceModelStatus.ERROR)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
