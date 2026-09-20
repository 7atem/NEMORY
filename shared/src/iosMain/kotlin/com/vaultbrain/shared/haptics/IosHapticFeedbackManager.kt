package com.vaultbrain.shared.haptics

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

class IosHapticFeedbackManager : HapticFeedbackManager {
    
    private val lightImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val mediumImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavyImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notificationFeedback = UINotificationFeedbackGenerator()

    override fun impactLight() {
        lightImpact.prepare()
        lightImpact.impactOccurred()
    }

    override fun impactMedium() {
        mediumImpact.prepare()
        mediumImpact.impactOccurred()
    }

    override fun impactHeavy() {
        heavyImpact.prepare()
        heavyImpact.impactOccurred()
    }

    override fun notifySuccess() {
        notificationFeedback.prepare()
        notificationFeedback.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }

    override fun notifyError() {
        notificationFeedback.prepare()
        notificationFeedback.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    }
}
