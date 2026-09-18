package com.vaultbrain.core.ai.llm.llama

import android.util.Log

object LlamaBridge {
    private const val TAG = "LlamaBridge"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("nemory_llama")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded native library nemory_llama")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library nemory_llama", e)
            isNativeLoaded = false
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    external fun nativeLoad(
        modelPath: String,
        mmprojPath: String?,
        ctxSize: Int,
        nThreads: Int
    ): Long

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        jpegBytes: ByteArray?,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        callback: LlamaTokenCallback?
    ): String?

    external fun nativeFree(handle: Long)
}

fun interface LlamaTokenCallback {
    fun onToken(piece: String): Boolean
}
