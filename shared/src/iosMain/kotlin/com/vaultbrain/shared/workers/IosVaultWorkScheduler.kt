package com.vaultbrain.shared.workers

import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSError

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class IosVaultWorkScheduler : VaultWorkScheduler {

    override fun scheduleOneOffJob(jobType: VaultJobType) {
        val identifier = "com.vaultbrain.task.${jobType.name.lowercase()}"
        val request = BGProcessingTaskRequest(identifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            println("Failed to submit BGProcessingTaskRequest: ${e.message}")
        }
    }

    override fun schedulePeriodicJob(jobType: VaultJobType, repeatIntervalHours: Long) {
        val identifier = "com.vaultbrain.task.periodic.${jobType.name.lowercase()}"
        val request = BGAppRefreshTaskRequest(identifier)
        
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            println("Failed to submit BGAppRefreshTaskRequest: ${e.message}")
        }
    }

    override fun cancelJob(jobType: VaultJobType) {
        val identifier = "com.vaultbrain.task.${jobType.name.lowercase()}"
        val periodicIdentifier = "com.vaultbrain.task.periodic.${jobType.name.lowercase()}"
        
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(identifier)
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(periodicIdentifier)
    }
}
