package com.vaultbrain.shared.connectors

import platform.Foundation.NSUserDefaults
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultWidgetManager : VaultWidgetManager {
    
    override fun requestWidgetUpdate() {
        // Since WidgetKit is not directly exported in this KMP version, 
        // we write to the shared App Group UserDefaults to signal the Swift widget extension to reload.
        val sharedDefaults = NSUserDefaults("group.com.nemory.app")
        sharedDefaults.setBool(true, "widget_needs_update")
        sharedDefaults.synchronize()
    }

    override fun clearLegacyState() {
        val sharedDefaults = NSUserDefaults("group.com.nemory.app")
        sharedDefaults.removeObjectForKey("legacy_widget_data")
        sharedDefaults.synchronize()
    }
}
