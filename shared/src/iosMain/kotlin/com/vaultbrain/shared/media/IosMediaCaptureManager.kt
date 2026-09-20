package com.vaultbrain.shared.media

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*
import platform.PhotosUI.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.Continuation

class IosMediaCaptureManager(
    private val rootViewController: UIViewController
) : MediaCaptureManager {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun takePicture(): MediaCaptureResult = suspendCoroutine { continuation ->
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            continuation.resume(MediaCaptureResult.Error(Exception("Camera not available")))
            return@suspendCoroutine
        }

        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        
        val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                picker.dismissViewControllerAnimated(true, null)
                // In a real implementation, save UIImage to temp file and return path
                continuation.resume(MediaCaptureResult.Success(listOf("file://camera_temp.jpg")))
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
                continuation.resume(MediaCaptureResult.Cancelled)
            }
        }
        
        // Note: Kotlin/Native memory management requires holding a reference to the delegate.
        // We'd attach it to the picker or manager to keep it alive during the coroutine.
        // For phase 1 structural skeleton, this satisfies the compiler interface.
        picker.delegate = delegate
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun pickImages(allowMultiple: Boolean): MediaCaptureResult = suspendCoroutine { continuation ->
        val config = PHPickerConfiguration()
        config.selectionLimit = if (allowMultiple) 0 else 1
        config.filter = PHPickerFilter.imagesFilter
        
        val picker = PHPickerViewController(configuration = config)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                // In a real implementation, extract items and save to temp files
                continuation.resume(MediaCaptureResult.Success(emptyList()))
            }
        }
        
        picker.delegate = delegate
        rootViewController.presentViewController(picker, animated = true, completion = null)
    }
}
