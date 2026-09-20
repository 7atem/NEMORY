package com.vaultbrain.shared.haptics

/**
 * Cross-platform haptic feedback generator to make UI interactions feel native and premium.
 */
interface HapticFeedbackManager {
    /** Light tap, used for minor state changes or toggles. */
    fun impactLight()
    
    /** Medium tap, used for standard button presses or confirmations. */
    fun impactMedium()
    
    /** Heavy tap, used for major actions or warnings. */
    fun impactHeavy()
    
    /** Success vibration pattern. */
    fun notifySuccess()
    
    /** Error/warning vibration pattern. */
    fun notifyError()
}

/** CompositionLocal for providing the platform-specific HapticFeedbackManager. */
val LocalHapticFeedback = androidx.compose.runtime.staticCompositionLocalOf<HapticFeedbackManager?> { null }
