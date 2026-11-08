package app.fitfat.ui.components

import app.fitfat.data.AppSettings
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * Taptic Engine: шаг — «выбор» (как у барабанов iOS), щелчок — лёгкий удар, упор — сильный.
 * Звук — системные щелчки клавиатуры (1104/1105): свой синтезированный щелчок, как на Android, появится позже.
 */
actual object Feedback {
    private val lightGen by lazy { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }
    private val heavyGen by lazy { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy) }
    private val selectGen by lazy { UISelectionFeedbackGenerator() }
    private val on: Boolean get() = AppSettings.haptic.value

    actual fun tick() { if (!on) return; AudioServicesPlaySystemSound(1104u); lightGen.impactOccurred() }
    actual fun step() { if (!on) return; AudioServicesPlaySystemSound(1104u); selectGen.selectionChanged() }
    actual fun tickSilent() { if (!on) return; lightGen.impactOccurred() }
    actual fun bump() { if (!on) return; AudioServicesPlaySystemSound(1105u); heavyGen.impactOccurred() }
    actual fun light() { if (!on) return; lightGen.impactOccurred() }
}
