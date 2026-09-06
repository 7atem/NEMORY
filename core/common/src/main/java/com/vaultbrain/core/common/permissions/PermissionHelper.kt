package com.vaultbrain.core.common.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralizes runtime permission checks for VaultBrain features.
 *
 * Requesting permissions still needs an Activity or an ActivityResultLauncher; this helper
 * only reports whether a permission is required and granted.
 */
object PermissionHelper {

    fun hasCamera(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CAMERA)

    fun hasRecordAudio(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun hasPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    /** Capture asks only for the camera. Notifications are requested contextually when the
     * user creates a reminder; denying them must never block saving an item. */
    fun capturePermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
