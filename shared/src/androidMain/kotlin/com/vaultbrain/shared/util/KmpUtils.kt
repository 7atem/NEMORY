package com.vaultbrain.shared.util

import java.util.UUID

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun randomUUIDString(): String = UUID.randomUUID().toString()
