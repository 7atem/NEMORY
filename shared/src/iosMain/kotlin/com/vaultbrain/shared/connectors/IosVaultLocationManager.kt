package com.vaultbrain.shared.connectors

import platform.CoreLocation.*
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents

class IosVaultLocationManager : VaultLocationManager {

    private val locationManager = CLLocationManager()
    private val delegate = LocationDelegate()
    
    private var permissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private var locationContinuation: kotlin.coroutines.Continuation<VaultLocation?>? = null

    init {
        locationManager.delegate = delegate
    }

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways) {
            continuation.resume(true)
        } else {
            permissionContinuation = continuation
            locationManager.requestWhenInUseAuthorization()
        }
    }

    override fun hasPermission(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getCurrentLocation(): VaultLocation? = suspendCoroutine { continuation ->
        if (!hasPermission()) {
            continuation.resume(null)
            return@suspendCoroutine
        }
        locationContinuation = continuation
        locationManager.requestLocation()
    }

    private inner class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: Int) {
            permissionContinuation?.let {
                it.resume(didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse || 
                          didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedAlways)
                permissionContinuation = null
            }
        }

        @OptIn(ExperimentalForeignApi::class)
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation
            locationContinuation?.let {
                if (location != null) {
                    it.resume(VaultLocation(
                        latitude = location.coordinate.useContents { latitude },
                        longitude = location.coordinate.useContents { longitude },
                        accuracy = location.horizontalAccuracy
                    ))
                } else {
                    it.resume(null)
                }
                locationContinuation = null
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            locationContinuation?.resume(null)
            locationContinuation = null
        }
    }
}
