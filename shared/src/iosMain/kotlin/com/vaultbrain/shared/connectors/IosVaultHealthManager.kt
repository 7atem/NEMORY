package com.vaultbrain.shared.connectors

import platform.HealthKit.*
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultHealthManager : VaultHealthManager {

    private val healthStore = HKHealthStore()

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        if (!HKHealthStore.isHealthDataAvailable()) {
            continuation.resume(false)
            return@suspendCoroutine
        }

        val workoutType = HKObjectType.workoutType()
        val typesToRead = setOf(workoutType)

        healthStore.requestAuthorizationToShareTypes(null, readTypes = typesToRead) { success, _ ->
            continuation.resume(success)
        }
    }

    override fun hasPermission(): Boolean {
        if (!HKHealthStore.isHealthDataAvailable()) return false
        val workoutType = HKObjectType.workoutType()
        val status = healthStore.authorizationStatusForType(workoutType)
        return status == HKAuthorizationStatusSharingAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getRecentWorkouts(): List<WorkoutData> = suspendCoroutine { continuation ->
        if (!hasPermission()) {
            continuation.resume(emptyList())
            return@suspendCoroutine
        }

        val workoutType = HKObjectType.workoutType()
        val sortDescriptor = NSSortDescriptor(key = HKSampleSortIdentifierEndDate, ascending = false)
        
        val query = HKSampleQuery(
            sampleType = workoutType,
            predicate = null,
            limit = 10u, // Fetch last 10 workouts
            sortDescriptors = listOf(sortDescriptor)
        ) { _, results, _ ->
            val workouts = results?.mapNotNull { it as? HKWorkout }?.map { workout ->
                WorkoutData(
                    id = workout.UUID.UUIDString,
                    type = workout.workoutActivityType.toString(),
                    durationMinutes = (workout.duration / 60).toInt(),
                    dateMs = (workout.endDate.timeIntervalSince1970 * 1000).toLong()
                )
            } ?: emptyList()
            
            continuation.resume(workouts)
        }
        
        healthStore.executeQuery(query)
    }
}
