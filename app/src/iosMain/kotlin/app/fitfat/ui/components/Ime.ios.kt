package app.fitfat.ui.components

import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication

/** На iOS клавиатура уходит своей анимацией — просто снимаем фокус с первого ответчика. */
actual fun hideImeFast(done: () -> Unit) {
    UIApplication.sharedApplication.sendAction(NSSelectorFromString("resignFirstResponder"), to = null, from = null, forEvent = null)
    done()
}
