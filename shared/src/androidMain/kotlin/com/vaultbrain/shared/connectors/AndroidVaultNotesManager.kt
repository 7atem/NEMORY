package com.vaultbrain.shared.connectors

class AndroidVaultNotesManager : VaultNotesManager {
    override suspend fun requestPermission(): Boolean = false
    override fun hasPermission(): Boolean = false
    override suspend fun getNotes(): List<String> = emptyList()
}
